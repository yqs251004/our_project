package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventSubscribersAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None,
    subscriberId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[DomainEventSubscriberStatus]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventSubscriberStatus]] =
    IO {
      requireOpsAdmin(context)
      val subscribers = subscriberStatuses(context, asOf.getOrElse(Instant.now()), subscriberId.filter(_.nonEmpty))
      paged(
        subscribers,
        Vector(
          asOf.map(value => "asOf" -> value.toString),
          subscriberId.filter(_.nonEmpty).map("subscriberId" -> _)
        ).flatten.toMap
      )
    }

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def paged(items: Vector[DomainEventSubscriberStatus], appliedFilters: Map[String, String]): PagedResponse[DomainEventSubscriberStatus] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)

  private def subscriberStatuses(
      context: ApiPlanContext,
      asOf: Instant,
      subscriberId: Option[String]
  ): Vector[DomainEventSubscriberStatus] =
    context.support.opsAnalyticsModule.domainEventSubscribers
      .filter(subscriber => subscriberId.forall(_ == subscriber.subscriberId))
      .map(subscriber => subscriberStatus(context, subscriber, asOf))
      .sortBy(status => (status.subscriberId, status.partitionStrategy))

  private def subscriberStatus(
      context: ApiPlanContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): DomainEventSubscriberStatus =
    val partitions = buildPartitionStatuses(context, subscriber, asOf)
    val lastDeliveredAt = partitions.flatMap(_.lastDeliveredAt).sorted.lastOption
    val oldestUndeliveredOccurredAt = partitions.flatMap(_.nextUndeliveredOccurredAt).sorted.headOption

    DomainEventSubscriberStatus(
      subscriberId = subscriber.subscriberId,
      partitionStrategy = subscriber.partitionStrategy.toString,
      partitionCount = partitions.size,
      laggingPartitionCount = partitions.count(_.undeliveredCount > 0),
      blockedPartitionCount = partitions.count(status =>
        status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
          status.blockedByInFlightProcessing || status.blockedBySequenceGap
      ),
      totalUndeliveredCount = partitions.map(_.undeliveredCount).sum,
      deadLetterUndeliveredCount = partitions.map(_.deadLetterUndeliveredCount).sum,
      quarantinedUndeliveredCount = partitions.map(_.quarantinedUndeliveredCount).sum,
      readyUndeliveredCount = partitions.map(_.readyUndeliveredCount).sum,
      maxSequenceLag = partitions.map(sequenceLag).foldLeft(0L)(math.max),
      oldestUndeliveredOccurredAt = oldestUndeliveredOccurredAt,
      lastDeliveredAt = lastDeliveredAt
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

  private def sequenceLag(status: DomainEventSubscriberPartitionStatus): Long =
    (status.cursor, status.nextUndeliveredSequenceNo) match
      case (_, None) => 0L
      case (Some(cursor), Some(sequenceNo)) =>
        math.max(0L, sequenceNo - cursor.lastDeliveredSequenceNo)
      case (None, Some(sequenceNo)) =>
        sequenceNo
