package riichinexus.infrastructure.events

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

private object DomainEventSerializationSupport:
  def serialize(event: DomainEvent): String =
    write[DomainEvent](event)

  def deserialize(payload: String): DomainEvent =
    read[DomainEvent](payload)

private object DomainEventRoutingSupport:
  final case class Route(
      aggregateType: String,
      aggregateId: String
  )

  def route(event: DomainEvent): Route =
    event match
      case MatchRecordArchived(tableId, _, _, _, _, _) =>
        Route("table", tableId.value)
      case AppealTicketFiled(ticket, _) =>
        Route("appeal-ticket", ticket.id.value)
      case AppealTicketResolved(ticket, _) =>
        Route("appeal-ticket", ticket.id.value)
      case AppealTicketWorkflowUpdated(ticket, _) =>
        Route("appeal-ticket", ticket.id.value)
      case AppealTicketReopened(ticket, _) =>
        Route("appeal-ticket", ticket.id.value)
      case AppealTicketAdjudicated(ticket, _, _, _) =>
        Route("appeal-ticket", ticket.id.value)
      case TournamentSettlementRecorded(settlement, _) =>
        Route("tournament-settlement", settlement.id.value)
      case GlobalDictionaryUpdated(entry, _) =>
        Route("global-dictionary", entry.key)
      case PlayerBanned(playerId, _, _) =>
        Route("player", playerId.value)
      case ClubDissolved(clubId, _) =>
        Route("club", clubId.value)

private final case class DeliveryAttemptSummary(
    allSubscribersDelivered: Boolean,
    deferredSubscribers: Vector[String]
)

final class OutboxBackedDomainEventBus(
    outboxRepository: DomainEventOutboxRepository,
    deliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    subscriberCursorRepository: DomainEventSubscriberCursorRepository,
    transactionManager: TransactionManager,
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty,
    retryDelay: Duration = Duration.ofSeconds(30),
    deferredRedeliveryDelay: Duration = Duration.ofSeconds(2),
    maxAttempts: Int = 5,
    pollInterval: Duration = Duration.ofSeconds(2)
) extends DomainEventBus:
  private val subscribers = mutable.ArrayBuffer.empty[DomainEventSubscriber]
  initialSubscribers.foreach(register)

  private val drainInFlight = AtomicBoolean(false)
  private val executor =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory:
      override def newThread(runnable: Runnable): Thread =
        val thread = Thread(runnable, "riichinexus-domain-event-outbox")
        thread.setDaemon(true)
        thread
    )

  executor.scheduleWithFixedDelay(
    () => scheduleDrain(),
    pollInterval.toMillis,
    pollInterval.toMillis,
    TimeUnit.MILLISECONDS
  )

  override def publish(event: DomainEvent): Unit =
    val route = DomainEventRoutingSupport.route(event)
    transactionManager.inTransaction {
      outboxRepository.save(
        DomainEventOutboxRecord.pending(
          eventType = event.getClass.getSimpleName,
          aggregateType = route.aggregateType,
          aggregateId = route.aggregateId,
          payload = DomainEventSerializationSupport.serialize(event),
          occurredAt = event.occurredAt,
          availableAt = event.occurredAt
        )
      )
    }
    scheduleDrain()

  override def register(subscriber: DomainEventSubscriber): Unit =
    require(
      !subscribers.exists(_.subscriberId == subscriber.subscriberId),
      s"Duplicate domain event subscriberId registered: ${subscriber.subscriberId}"
    )
    subscribers += subscriber

  override def drainPendingNow(limit: Int = 100, processedAt: Instant = Instant.now()): Int =
    processPending(limit, processedAt)

  def processPending(limit: Int = 100, processedAt: Instant = Instant.now()): Int =
    if !drainInFlight.compareAndSet(false, true) then 0
    else
      try
        val runnableRecords = outboxRepository.findPending(limit, processedAt)
        runnableRecords.foreach(processRecord(_, processedAt))
        runnableRecords.size
      finally
        drainInFlight.set(false)

  private def scheduleDrain(): Unit =
    executor.execute(() => processPending())

  private def processRecord(record: DomainEventOutboxRecord, processedAt: Instant): Unit =
    val claimed =
      try
        transactionManager.inTransaction {
          val current = outboxRepository.findById(record.id).getOrElse(record)
          if !current.isRunnable(processedAt) then None
          else Some(outboxRepository.save(current.markProcessing(processedAt)))
        }
      catch
        case _: OptimisticConcurrencyException =>
          None

    claimed.foreach { processingRecord =>
      try
        val event = DomainEventSerializationSupport.deserialize(processingRecord.payload)
        val deliverySummary = deliverToSubscribers(event, processingRecord, processedAt)
        transactionManager.inTransaction {
          val refreshed = outboxRepository.findById(processingRecord.id).getOrElse(processingRecord)
          val updated =
            if deliverySummary.allSubscribersDelivered then refreshed.markCompleted(processedAt)
            else
              refreshed.markDeferred(
                processedAt.plus(deferredRedeliveryDelay),
                s"deferred-for-subscriber-order:${deliverySummary.deferredSubscribers.mkString(",")}"
              )
          outboxRepository.save(updated)
        }
      catch
        case error: Throwable =>
          val errorMessage = Option(error.getMessage).filter(_.trim.nonEmpty).getOrElse(error.getClass.getSimpleName)
          transactionManager.inTransaction {
            val refreshed = outboxRepository.findById(processingRecord.id).getOrElse(processingRecord)
            val updated =
              if refreshed.attempts >= maxAttempts then refreshed.markDeadLetter(errorMessage, processedAt)
              else refreshed.markRetryScheduled(errorMessage, processedAt.plus(retryDelay.multipliedBy(refreshed.attempts.toLong.max(1L))))
            outboxRepository.save(updated)
          }
    }

  private def deliverToSubscribers(
      event: DomainEvent,
      record: DomainEventOutboxRecord,
      deliveredAt: Instant
  ): DeliveryAttemptSummary =
    val deferredSubscribers = Vector.newBuilder[String]

    subscribers.foreach { subscriber =>
      val alreadyDelivered = transactionManager.inTransaction {
        deliveryReceiptRepository.findByOutboxRecordAndSubscriber(record.id, subscriber.subscriberId).nonEmpty
      }

      if !alreadyDelivered then
        val partitionKey = subscriber.partitionStrategy.partitionKey(record)
        val ready = transactionManager.inTransaction {
          isReadyForSubscriber(subscriber, record, partitionKey)
        }

        if ready then
          subscriber.handle(event)
          transactionManager.inTransaction {
            deliveryReceiptRepository.save(
              DomainEventDeliveryReceipt.delivered(
                outboxRecordId = record.id,
                subscriberId = subscriber.subscriberId,
                eventType = record.eventType,
                deliveredAt = deliveredAt,
                attemptNo = record.attempts
              )
            )
            advanceCursor(subscriber, partitionKey, record, deliveredAt)
          }
        else deferredSubscribers += subscriber.subscriberId
    }

    val deferred = deferredSubscribers.result()
    DeliveryAttemptSummary(
      allSubscribersDelivered = deferred.isEmpty,
      deferredSubscribers = deferred
    )

  private def isReadyForSubscriber(
      subscriber: DomainEventSubscriber,
      record: DomainEventOutboxRecord,
      partitionKey: String
  ): Boolean =
    val cursor = subscriberCursorRepository.findBySubscriberAndPartition(subscriber.subscriberId, partitionKey)
    val lastDeliveredSequenceNo = cursor.map(_.lastDeliveredSequenceNo).getOrElse(0L)

    if record.sequenceNo <= lastDeliveredSequenceNo then true
    else
      !outboxRepository.findAll().exists { candidate =>
        candidate.sequenceNo < record.sequenceNo &&
        subscriber.partitionStrategy.partitionKey(candidate) == partitionKey &&
        deliveryReceiptRepository.findByOutboxRecordAndSubscriber(candidate.id, subscriber.subscriberId).isEmpty
      }

  private def advanceCursor(
      subscriber: DomainEventSubscriber,
      partitionKey: String,
      record: DomainEventOutboxRecord,
      deliveredAt: Instant
  ): DomainEventSubscriberCursor =
    val nextCursor =
      subscriberCursorRepository.findBySubscriberAndPartition(subscriber.subscriberId, partitionKey) match
        case Some(existing) =>
          existing.advance(record.id, record.sequenceNo, deliveredAt)
        case None =>
          DomainEventSubscriberCursor.advanced(
            subscriberId = subscriber.subscriberId,
            partitionKey = partitionKey,
            outboxRecordId = record.id,
            sequenceNo = record.sequenceNo,
            advancedAt = deliveredAt
          )

    subscriberCursorRepository.save(nextCursor)
