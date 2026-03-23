package riichinexus.infrastructure.events

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.DomainEvent
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

private object DomainEventSerializationSupport:
  def serialize(event: DomainEvent): String =
    write[DomainEvent](event)

  def deserialize(payload: String): DomainEvent =
    read[DomainEvent](payload)

final class OutboxBackedDomainEventBus(
    outboxRepository: DomainEventOutboxRepository,
    transactionManager: TransactionManager,
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty,
    retryDelay: Duration = Duration.ofSeconds(30),
    maxAttempts: Int = 5,
    pollInterval: Duration = Duration.ofSeconds(2)
) extends DomainEventBus:
  private val subscribers = mutable.ArrayBuffer.from(initialSubscribers)
  private val drainInFlight = AtomicBoolean(false)
  private val executor =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory:
      override def newThread(runnable: Runnable): Thread =
        val thread = Thread(runnable, "riichinexus-domain-event-outbox")
        thread.setDaemon(true)
        thread
    )

  executor.scheduleWithFixedDelay(
    () => drainPending(),
    pollInterval.toMillis,
    pollInterval.toMillis,
    TimeUnit.MILLISECONDS
  )

  override def publish(event: DomainEvent): Unit =
    transactionManager.inTransaction {
      outboxRepository.save(
        DomainEventOutboxRecord.pending(
          eventType = event.getClass.getSimpleName,
          payload = DomainEventSerializationSupport.serialize(event),
          occurredAt = event.occurredAt,
          availableAt = event.occurredAt
        )
      )
    }
    drainPending()

  override def register(subscriber: DomainEventSubscriber): Unit =
    subscribers += subscriber

  def processPending(limit: Int = 100, processedAt: Instant = Instant.now()): Int =
    if !drainInFlight.compareAndSet(false, true) then 0
    else
      try
        val runnableRecords = outboxRepository.findPending(limit, processedAt)
        runnableRecords.foreach(processRecord(_, processedAt))
        runnableRecords.size
      finally
        drainInFlight.set(false)

  private def drainPending(): Unit =
    executor.execute(() => processPending())

  private def processRecord(record: DomainEventOutboxRecord, processedAt: Instant): Unit =
    val claimed = transactionManager.inTransaction {
      val current = outboxRepository.findById(record.id).getOrElse(record)
      if !current.isRunnable(processedAt) then current
      else outboxRepository.save(current.markProcessing(processedAt))
    }

    if claimed.status == DomainEventOutboxStatus.Processing then
      try
        val event = DomainEventSerializationSupport.deserialize(claimed.payload)
        subscribers.foreach(_.handle(event))
        transactionManager.inTransaction {
          outboxRepository.save(claimed.markCompleted(processedAt))
        }
      catch
        case error: Throwable =>
          val errorMessage = Option(error.getMessage).filter(_.trim.nonEmpty).getOrElse(error.getClass.getSimpleName)
          transactionManager.inTransaction {
            val refreshed = outboxRepository.findById(claimed.id).getOrElse(claimed)
            val updated =
              if refreshed.attempts >= maxAttempts then refreshed.markDeadLetter(errorMessage, processedAt)
              else refreshed.markRetryScheduled(errorMessage, processedAt.plus(retryDelay.multipliedBy(refreshed.attempts.toLong.max(1L))))
            outboxRepository.save(updated)
          }

