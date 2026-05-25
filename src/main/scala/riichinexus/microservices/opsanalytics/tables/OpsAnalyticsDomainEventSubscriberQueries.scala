package riichinexus.microservices.opsanalytics.tables

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*

private[opsanalytics] object OpsAnalyticsDomainEventSubscriberQueries:
  def resolveSubscriber(module: OpsAnalyticsModuleContext, subscriberId: String): DomainEventSubscriber =
    module.domainEventSubscribers
      .find(_.subscriberId == subscriberId)
      .getOrElse(throw NoSuchElementException(s"Domain event subscriber $subscriberId was not registered"))

  def subscriberStatuses(
      module: OpsAnalyticsModuleContext,
      asOf: Instant,
      subscriberId: Option[String]
  ): Vector[DomainEventSubscriberStatus] =
    module.domainEventSubscribers
      .filter(subscriber => subscriberId.forall(_ == subscriber.subscriberId))
      .map(subscriber => subscriberStatus(module, subscriber, asOf))
      .sortBy(status => (status.subscriberId, status.partitionStrategy))

  def subscriberPartitionStatuses(
      module: OpsAnalyticsModuleContext,
      subscriberId: String,
      asOf: Instant,
      lagOnly: Boolean,
      blockedOnly: Boolean,
      partitionKey: Option[String]
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val subscriber = resolveSubscriber(module, subscriberId)
    partitionStatuses(module, subscriber, asOf)
      .filter(status => partitionKey.forall(_ == status.partitionKey))
      .filter(status => !lagOnly || status.undeliveredCount > 0)
      .filter(status => !blockedOnly || partitionBlocked(status))
      .sortBy(status => (status.partitionKey, status.nextUndeliveredSequenceNo.getOrElse(Long.MaxValue)))

  def isBlockedForSubscriber(
      module: OpsAnalyticsModuleContext,
      record: DomainEventOutboxRecord,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Boolean =
    partitionStatuses(module, subscriber, asOf)
      .find(_.partitionKey == subscriber.partitionStrategy.partitionKey(record))
      .exists(status => status.nextUndeliveredRecordId.contains(record.id) && partitionBlocked(status))

  def subscriberHasBlockedPartition(
      module: OpsAnalyticsModuleContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Boolean =
    partitionStatuses(module, subscriber, asOf).exists(partitionBlocked)

  def partitionStatuses(
      module: OpsAnalyticsModuleContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Vector[DomainEventSubscriberPartitionStatus] =
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

  private def subscriberStatus(
      module: OpsAnalyticsModuleContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): DomainEventSubscriberStatus =
    val partitions = partitionStatuses(module, subscriber, asOf)
    val lastDeliveredAt = partitions.flatMap(_.lastDeliveredAt).sorted.lastOption
    val oldestUndeliveredOccurredAt = partitions.flatMap(_.nextUndeliveredOccurredAt).sorted.headOption

    DomainEventSubscriberStatus(
      subscriberId = subscriber.subscriberId,
      partitionStrategy = subscriber.partitionStrategy.toString,
      partitionCount = partitions.size,
      laggingPartitionCount = partitions.count(_.undeliveredCount > 0),
      blockedPartitionCount = partitions.count(partitionBlocked),
      totalUndeliveredCount = partitions.map(_.undeliveredCount).sum,
      deadLetterUndeliveredCount = partitions.map(_.deadLetterUndeliveredCount).sum,
      quarantinedUndeliveredCount = partitions.map(_.quarantinedUndeliveredCount).sum,
      readyUndeliveredCount = partitions.map(_.readyUndeliveredCount).sum,
      maxSequenceLag = partitions.map(sequenceLag).foldLeft(0L)(math.max),
      oldestUndeliveredOccurredAt = oldestUndeliveredOccurredAt,
      lastDeliveredAt = lastDeliveredAt
    )

  private def partitionBlocked(status: DomainEventSubscriberPartitionStatus): Boolean =
    status.blockedByDeadLetter ||
      status.blockedByQuarantine ||
      status.blockedByRetryDelay ||
      status.blockedByInFlightProcessing ||
      status.blockedBySequenceGap

  private def sequenceLag(status: DomainEventSubscriberPartitionStatus): Long =
    (status.cursor, status.nextUndeliveredSequenceNo) match
      case (_, None) => 0L
      case (Some(cursor), Some(sequenceNo)) =>
        math.max(0L, sequenceNo - cursor.lastDeliveredSequenceNo)
      case (None, Some(sequenceNo)) =>
        sequenceNo
