package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsDomainEventsSummaryAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None
) extends APIMessage[DomainEventBusSummary] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventBusSummary] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      val resolvedAsOf = asOf.getOrElse(Instant.now())
      val records = context.support.opsAnalyticsModule.domainEventOutboxRepository.findAll().sortBy(_.sequenceNo)
      val blockedSubscriberCount = context.support.opsAnalyticsModule.domainEventSubscribers.count { subscriber =>
        buildPartitionStatuses(context, subscriber, resolvedAsOf).exists(status =>
          status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
            status.blockedByInFlightProcessing || status.blockedBySequenceGap
        )
      }
      DomainEventBusSummary(
        asOf = resolvedAsOf,
        registeredSubscriberCount = context.support.opsAnalyticsModule.domainEventSubscribers.size,
        cursorCount = context.support.opsAnalyticsModule.domainEventSubscriberCursorRepository.findAll().size,
        pendingCount = records.count(_.status == DomainEventOutboxStatus.Pending),
        scheduledPendingCount = records.count(record =>
          record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(resolvedAsOf)
        ),
        processingCount = records.count(_.status == DomainEventOutboxStatus.Processing),
        completedCount = records.count(_.status == DomainEventOutboxStatus.Completed),
        deadLetterCount = records.count(_.status == DomainEventOutboxStatus.DeadLetter),
        quarantinedCount = records.count(_.status == DomainEventOutboxStatus.Quarantined),
        highestAssignedSequenceNo = records.lastOption.map(_.sequenceNo),
        nextRunnableSequenceNo = records.find(_.isRunnable(resolvedAsOf)).map(_.sequenceNo),
        oldestPendingOccurredAt = records.find(_.status == DomainEventOutboxStatus.Pending).map(_.occurredAt),
        oldestDeadLetterOccurredAt = records.find(_.status == DomainEventOutboxStatus.DeadLetter).map(_.occurredAt),
        oldestQuarantinedOccurredAt = records.find(_.status == DomainEventOutboxStatus.Quarantined).map(_.occurredAt),
        blockedSubscriberCount = blockedSubscriberCount
      )
    }

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
