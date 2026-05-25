package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.domain.model.{
  AuditEventEntry as DomainAuditEventEntry,
  DomainEventBusSummary as DomainDomainEventBusSummary,
  DomainEventDeliveryReceipt as DomainDomainEventDeliveryReceipt,
  DomainEventOutboxBatchOperationResult as DomainDomainEventOutboxBatchOperationResult,
  DomainEventOutboxRecord as DomainDomainEventOutboxRecord,
  DomainEventOutboxStatus,
  DomainEventSubscriberPartitionStatus as DomainDomainEventSubscriberPartitionStatus,
  DomainEventSubscriberStatus as DomainDomainEventSubscriberStatus,
  EventCascadeRecord as DomainEventCascadeRecord,
  EventCascadeStatus
}
import upickle.default.*

type DomainEventDeliveryStatus = String

type DomainEventSubscriberStatusKind = String

private def deliveryStatus(status: DomainEventOutboxStatus): String =
  status match
    case DomainEventOutboxStatus.Completed   => "Delivered"
    case DomainEventOutboxStatus.DeadLetter  => "Failed"
    case DomainEventOutboxStatus.Quarantined => "Quarantined"
    case DomainEventOutboxStatus.Pending     => "Pending"
    case DomainEventOutboxStatus.Processing  => "Pending"

final case class DomainEventOutboxRecord(
    id: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    payload: ujson.Value,
    occurredAt: String,
    status: DomainEventDeliveryStatus,
    attemptCount: Int,
    nextAttemptAt: Option[String],
    lastError: Option[String]
) derives ReadWriter

object DomainEventOutboxRecord:
  def fromDomain(record: DomainDomainEventOutboxRecord): DomainEventOutboxRecord =
    DomainEventOutboxRecord(
      id = record.id.value,
      aggregateType = record.aggregateType,
      aggregateId = record.aggregateId,
      eventType = record.eventType,
      payload = parsePayload(record.payload),
      occurredAt = record.occurredAt.toString,
      status = deliveryStatus(record.status),
      attemptCount = record.attempts,
      nextAttemptAt = Option.when(record.status == DomainEventOutboxStatus.Pending)(record.availableAt.toString),
      lastError = record.lastError
    )

  private def parsePayload(payload: String): ujson.Value =
    scala.util.Try(read[ujson.Value](payload)).getOrElse(ujson.Obj("raw" -> payload))

final case class AuditEventEntry(
    id: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    occurredAt: String,
    actorId: Option[String],
    details: Map[String, String],
    note: Option[String]
) derives ReadWriter

object AuditEventEntry:
  def fromDomain(entry: DomainAuditEventEntry): AuditEventEntry =
    AuditEventEntry(
      id = entry.id.value,
      aggregateType = entry.aggregateType,
      aggregateId = entry.aggregateId,
      eventType = entry.eventType,
      occurredAt = entry.occurredAt.toString,
      actorId = entry.actorId.map(_.value),
      details = entry.details,
      note = entry.note
    )

final case class DomainEventDeliveryReceipt(
    id: String,
    outboxId: String,
    subscriberId: String,
    deliveredAt: String,
    status: DomainEventDeliveryStatus,
    errorMessage: Option[String]
) derives ReadWriter

object DomainEventDeliveryReceipt:
  def fromDomain(receipt: DomainDomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    DomainEventDeliveryReceipt(
      id = receipt.id.value,
      outboxId = receipt.outboxRecordId.value,
      subscriberId = receipt.subscriberId,
      deliveredAt = receipt.deliveredAt.toString,
      status = "Delivered",
      errorMessage = None
    )

final case class DomainEventBusSummary(
    pendingCount: Int,
    deliveredCount: Int,
    failedCount: Int,
    quarantinedCount: Int
) derives ReadWriter

object DomainEventBusSummary:
  def fromDomain(summary: DomainDomainEventBusSummary): DomainEventBusSummary =
    DomainEventBusSummary(
      pendingCount = summary.pendingCount + summary.scheduledPendingCount + summary.processingCount,
      deliveredCount = summary.completedCount,
      failedCount = summary.deadLetterCount,
      quarantinedCount = summary.quarantinedCount
    )

final case class DomainEventOutboxHistoryView(
    record: DomainEventOutboxRecord,
    auditTrail: Vector[AuditEventEntry],
    deliveryReceipts: Vector[DomainEventDeliveryReceipt]
) derives ReadWriter

object DomainEventOutboxHistoryView:
  def fromDomain(
      record: DomainDomainEventOutboxRecord,
      auditTrail: Vector[DomainAuditEventEntry],
      deliveryReceipts: Vector[DomainDomainEventDeliveryReceipt]
  ): DomainEventOutboxHistoryView =
    DomainEventOutboxHistoryView(
      record = DomainEventOutboxRecord.fromDomain(record),
      auditTrail = auditTrail.map(AuditEventEntry.fromDomain),
      deliveryReceipts = deliveryReceipts.map(DomainEventDeliveryReceipt.fromDomain)
    )

final case class DomainEventOutboxBatchOperationResult(
    requestedCount: Int,
    affectedCount: Int,
    skippedCount: Int,
    affectedIds: Vector[String],
    skippedIds: Vector[String]
) derives ReadWriter

object DomainEventOutboxBatchOperationResult:
  def fromDomain(result: DomainDomainEventOutboxBatchOperationResult): DomainEventOutboxBatchOperationResult =
    DomainEventOutboxBatchOperationResult(
      requestedCount = result.requestedCount,
      affectedCount = result.succeededCount,
      skippedCount = result.failedCount,
      affectedIds = result.succeededRecordIds.map(_.value),
      skippedIds = result.failures.map(_.recordId.value)
    )

final case class DomainEventSubscriberStatus(
    subscriberId: String,
    status: DomainEventSubscriberStatusKind,
    lastDeliveredAt: Option[String],
    lastError: Option[String],
    lagCount: Long
) derives ReadWriter

object DomainEventSubscriberStatus:
  def fromDomain(status: DomainDomainEventSubscriberStatus): DomainEventSubscriberStatus =
    DomainEventSubscriberStatus(
      subscriberId = status.subscriberId,
      status = if status.blockedPartitionCount > 0 then "Failed" else "Active",
      lastDeliveredAt = status.lastDeliveredAt.map(_.toString),
      lastError = None,
      lagCount = status.totalUndeliveredCount.toLong
    )

final case class DomainEventSubscriberPartitionStatus(
    subscriberId: String,
    partitionKey: String,
    status: DomainEventSubscriberStatusKind,
    cursor: Option[String],
    lagCount: Long,
    lastDeliveredAt: Option[String],
    lastError: Option[String]
) derives ReadWriter

object DomainEventSubscriberPartitionStatus:
  def fromDomain(status: DomainDomainEventSubscriberPartitionStatus): DomainEventSubscriberPartitionStatus =
    DomainEventSubscriberPartitionStatus(
      subscriberId = status.subscriberId,
      partitionKey = status.partitionKey,
      status =
        if status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedBySequenceGap then "Failed"
        else "Active",
      cursor = status.cursor.map(_.lastDeliveredOutboxRecordId.value),
      lagCount = status.undeliveredCount.toLong,
      lastDeliveredAt = status.lastDeliveredAt.map(_.toString),
      lastError = status.nextUndeliveredStatus.map(_.toString)
    )

final case class EventCascadeRecord(
    id: String,
    sourceEventId: String,
    aggregateType: String,
    aggregateId: String,
    cascadeType: String,
    status: DomainEventDeliveryStatus,
    occurredAt: String,
    details: Map[String, String]
) derives ReadWriter

object EventCascadeRecord:
  def fromDomain(record: DomainEventCascadeRecord): EventCascadeRecord =
    EventCascadeRecord(
      id = record.id.value,
      sourceEventId = record.id.value,
      aggregateType = record.aggregateType,
      aggregateId = record.aggregateId,
      cascadeType = record.consumer.toString,
      status = if record.status == EventCascadeStatus.Completed then "Delivered" else "Pending",
      occurredAt = record.occurredAt.toString,
      details = record.metadata ++ Map("eventType" -> record.eventType, "summary" -> record.summary)
    )

object DomainEventResponses:
  type DomainEventBusSummaryResponse = DomainEventBusSummary
  type DomainEventOutboxRecordResponse = DomainEventOutboxRecord
  type DomainEventOutboxHistoryResponse = DomainEventOutboxHistoryView
  type DomainEventBatchOperationResponse = DomainEventOutboxBatchOperationResult
  type DomainEventSubscriberStatusResponse = DomainEventSubscriberStatus
  type DomainEventSubscriberPartitionStatusResponse = DomainEventSubscriberPartitionStatus
  type EventCascadeRecordResponse = EventCascadeRecord
