package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None,
    status: Option[DomainEventOutboxStatus] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    subscriberId: Option[String] = None,
    partitionKey: Option[String] = None,
    delivered: Option[Boolean] = None,
    blockedOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[DomainEventOutboxRecord]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventOutboxRecord]] =
    IO {
      requireOpsAdmin(context)
      val parsedAsOf = asOf.getOrElse(Instant.now())
      val parsedEventType = eventType.filter(_.nonEmpty)
      val parsedAggregateType = aggregateType.filter(_.nonEmpty)
      val parsedAggregateId = aggregateId.filter(_.nonEmpty)
      val parsedSubscriberId = subscriberId.filter(_.nonEmpty)
      val parsedPartitionKey = partitionKey.filter(_.nonEmpty)
      val parsedBlockedOnly = blockedOnly.getOrElse(false)
      require(parsedSubscriberId.nonEmpty || delivered.isEmpty, "Input field delivered requires subscriberId")
      require(parsedSubscriberId.nonEmpty || parsedPartitionKey.isEmpty, "Input field partitionKey requires subscriberId")
      require(parsedSubscriberId.nonEmpty || !parsedBlockedOnly, "Input field blockedOnly requires subscriberId")
      val subscriber = parsedSubscriberId.map(resolveSubscriber(context, _))
      val receiptsByOutboxAndSubscriber = context.support.opsAnalyticsModule.domainEventDeliveryReceiptRepository.findAll()
        .groupBy(receipt => receipt.outboxRecordId -> receipt.subscriberId)
      paged(
        context.support.opsAnalyticsModule.domainEventOutboxRepository.findAll()
          .filter(record => status.forall(_ == record.status))
          .filter(record => parsedEventType.forall(_ == record.eventType))
          .filter(record => parsedAggregateType.forall(_ == record.aggregateType))
          .filter(record => parsedAggregateId.forall(_ == record.aggregateId))
          .filter(record =>
            subscriber.forall(sub =>
              parsedPartitionKey.forall(_ == sub.partitionStrategy.partitionKey(record))
            )
          )
          .filter(record =>
            subscriber match
              case Some(sub) =>
                val hasReceipt = receiptsByOutboxAndSubscriber.contains(record.id -> sub.subscriberId)
                delivered.forall(_ == hasReceipt)
              case None =>
                delivered.isEmpty
          )
          .filter(record =>
            !parsedBlockedOnly || subscriber.isEmpty || isBlockedForSubscriber(context, record, subscriber.get, parsedAsOf)
          )
          .sortBy(_.sequenceNo),
        Vector(
          asOf.map(value => "asOf" -> value.toString),
          status.map(value => "status" -> value.toString),
          eventType.filter(_.nonEmpty).map("eventType" -> _),
          aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
          aggregateId.filter(_.nonEmpty).map("aggregateId" -> _),
          subscriberId.filter(_.nonEmpty).map("subscriberId" -> _),
          partitionKey.filter(_.nonEmpty).map("partitionKey" -> _),
          delivered.map(value => "delivered" -> value.toString),
          blockedOnly.map(value => "blockedOnly" -> value.toString)
        ).flatten.toMap
      )
    }

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def paged(items: Vector[DomainEventOutboxRecord], appliedFilters: Map[String, String]): PagedResponse[DomainEventOutboxRecord] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)

  private def resolveSubscriber(context: ApiPlanContext, subscriberId: String): DomainEventSubscriber =
    context.support.opsAnalyticsModule.domainEventSubscribers
      .find(_.subscriberId == subscriberId)
      .getOrElse(throw NoSuchElementException(s"Domain event subscriber $subscriberId was not registered"))

  private def isBlockedForSubscriber(
      context: ApiPlanContext,
      record: DomainEventOutboxRecord,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Boolean =
    buildPartitionStatuses(context, subscriber, asOf)
      .find(_.partitionKey == subscriber.partitionStrategy.partitionKey(record))
      .exists(status =>
        status.nextUndeliveredRecordId.contains(record.id) &&
          (status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
            status.blockedByInFlightProcessing || status.blockedBySequenceGap)
      )

  private def buildPartitionStatuses(
      context: ApiPlanContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val module = context.support.opsAnalyticsModule
    val records = module.domainEventOutboxRepository.findAll().sortBy(_.sequenceNo)
    val recordsById = records.map(record => record.id -> record).toMap
    val receipts = module.domainEventDeliveryReceiptRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val receiptByRecordId = receipts.map(receipt => receipt.outboxRecordId -> receipt).toMap
    val cursors = module.domainEventSubscriberCursorRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val cursorByPartition = cursors.map(cursor => cursor.partitionKey -> cursor).toMap
    val relevantPartitionsFromRecords = records.map(subscriber.partitionStrategy.partitionKey).distinct
    val relevantPartitions = (relevantPartitionsFromRecords ++ cursorByPartition.keys).distinct

    relevantPartitions.map { currentPartitionKey =>
      val partitionRecords = records.filter(record =>
        subscriber.partitionStrategy.partitionKey(record) == currentPartitionKey
      )
      val undeliveredRecords = partitionRecords.filterNot(record => receiptByRecordId.contains(record.id))
      val nextUndelivered = undeliveredRecords.headOption
      val lastDeliveredReceipt = partitionRecords.reverseIterator
        .map(record => receiptByRecordId.get(record.id))
        .collectFirst { case Some(receipt) => receipt }
      val cursor = cursorByPartition.get(currentPartitionKey)

      DomainEventSubscriberPartitionStatus(
        subscriberId = subscriber.subscriberId,
        partitionStrategy = subscriber.partitionStrategy.toString,
        partitionKey = currentPartitionKey,
        cursor = cursor,
        lastDeliveredAt = lastDeliveredReceipt.map(_.deliveredAt).orElse(cursor.map(_.advancedAt)),
        lastDeliveredSequenceNo =
          cursor.map(_.lastDeliveredSequenceNo)
            .orElse(lastDeliveredReceipt.flatMap(receipt => recordsById.get(receipt.outboxRecordId).map(_.sequenceNo))),
        undeliveredCount = undeliveredRecords.size,
        deadLetterUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.DeadLetter),
        quarantinedUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.Quarantined),
        readyUndeliveredCount = undeliveredRecords.count(record =>
          record.status == DomainEventOutboxStatus.Pending && !record.availableAt.isAfter(asOf)
        ),
        nextUndeliveredRecordId = nextUndelivered.map(_.id),
        nextUndeliveredSequenceNo = nextUndelivered.map(_.sequenceNo),
        nextUndeliveredEventType = nextUndelivered.map(_.eventType),
        nextUndeliveredStatus = nextUndelivered.map(_.status),
        nextUndeliveredOccurredAt = nextUndelivered.map(_.occurredAt),
        nextUndeliveredAvailableAt = nextUndelivered.map(_.availableAt),
        blockedByDeadLetter = nextUndelivered.exists(_.status == DomainEventOutboxStatus.DeadLetter),
        blockedByQuarantine = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Quarantined),
        blockedByRetryDelay = nextUndelivered.exists(record =>
          record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(asOf)
        ),
        blockedByInFlightProcessing = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Processing),
        blockedBySequenceGap = nextUndelivered.exists(record =>
          undeliveredRecords.exists(_.sequenceNo > record.sequenceNo)
        )
      )
    }
