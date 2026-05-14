package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.{AuditEventRepository, DomainEventDeliveryReceiptRepository, DomainEventOutboxRepository}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.opsanalytics.api.responses.DomainEventOutboxHistoryView

private[api] final class DomainEventOutboxQueryService(
    outboxRepository: DomainEventOutboxRepository,
    deliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    auditEventRepository: AuditEventRepository,
    authorizationService: AuthorizationService,
    subscriberStatusService: DomainEventSubscriberStatusService
):
  def outboxHistory(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal
  ): DomainEventOutboxHistoryView =
    authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
    val record = outboxRepository.findById(recordId)
      .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
    DomainEventOutboxHistoryView(
      record = record,
      auditTrail = auditEventRepository
        .findByAggregate("domain-event-outbox-record", recordId.value)
        .sortBy(entry => (entry.occurredAt, entry.id.value)),
      deliveryReceipts = deliveryReceiptRepository.findAll()
        .filter(_.outboxRecordId == recordId)
        .sortBy(receipt => (receipt.deliveredAt, receipt.subscriberId, receipt.id.value))
    )

  def summary(asOf: Instant = Instant.now()): DomainEventBusSummary =
    val records = sortedOutboxRecords()
    val subscriberStatuses = subscriberStatusService.subscriberStatuses(asOf)
    DomainEventBusSummary(
      asOf = asOf,
      registeredSubscriberCount = subscriberStatusService.subscriberCount,
      cursorCount = subscriberStatusService.cursorCount,
      pendingCount = records.count(_.status == DomainEventOutboxStatus.Pending),
      scheduledPendingCount = records.count(record =>
        record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(asOf)
      ),
      processingCount = records.count(_.status == DomainEventOutboxStatus.Processing),
      completedCount = records.count(_.status == DomainEventOutboxStatus.Completed),
      deadLetterCount = records.count(_.status == DomainEventOutboxStatus.DeadLetter),
      quarantinedCount = records.count(_.status == DomainEventOutboxStatus.Quarantined),
      highestAssignedSequenceNo = records.lastOption.map(_.sequenceNo),
      nextRunnableSequenceNo = records.find(_.isRunnable(asOf)).map(_.sequenceNo),
      oldestPendingOccurredAt = records.find(_.status == DomainEventOutboxStatus.Pending).map(_.occurredAt),
      oldestDeadLetterOccurredAt = records.find(_.status == DomainEventOutboxStatus.DeadLetter).map(_.occurredAt),
      oldestQuarantinedOccurredAt = records.find(_.status == DomainEventOutboxStatus.Quarantined).map(_.occurredAt),
      blockedSubscriberCount = subscriberStatuses.count(_.blockedPartitionCount > 0)
    )

  def outboxRecords(
      asOf: Instant = Instant.now(),
      status: Option[DomainEventOutboxStatus] = None,
      eventType: Option[String] = None,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      subscriberId: Option[String] = None,
      partitionKey: Option[String] = None,
      delivered: Option[Boolean] = None,
      blockedOnly: Boolean = false
  ): Vector[DomainEventOutboxRecord] =
    val subscriber = subscriberId.map(subscriberStatusService.resolveSubscriber)
    val receiptsByOutboxAndSubscriber = deliveryReceiptRepository.findAll()
      .groupBy(receipt => receipt.outboxRecordId -> receipt.subscriberId)
    val normalizedEventType = eventType.map(_.trim).filter(_.nonEmpty)
    val normalizedAggregateType = aggregateType.map(_.trim).filter(_.nonEmpty)
    val normalizedAggregateId = aggregateId.map(_.trim).filter(_.nonEmpty)
    val normalizedPartitionKey = partitionKey.map(_.trim).filter(_.nonEmpty)

    sortedOutboxRecords()
      .filter(record => status.forall(_ == record.status))
      .filter(record => normalizedEventType.forall(_ == record.eventType))
      .filter(record => normalizedAggregateType.forall(_ == record.aggregateType))
      .filter(record => normalizedAggregateId.forall(_ == record.aggregateId))
      .filter(record =>
        subscriber.forall(sub =>
          normalizedPartitionKey.forall(_ == sub.partitionStrategy.partitionKey(record))
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
        !blockedOnly || subscriber.isEmpty || subscriberStatusService.isBlockedForSubscriber(record, subscriber.get, asOf)
      )

  private def sortedOutboxRecords(): Vector[DomainEventOutboxRecord] =
    outboxRepository.findAll().sortBy(_.sequenceNo)
