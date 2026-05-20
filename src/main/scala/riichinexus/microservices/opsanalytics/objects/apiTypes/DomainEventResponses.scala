package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

type DomainEventDeliveryStatus = riichinexus.domain.model.DomainEventOutboxStatus

enum DomainEventSubscriberStatusKind derives CanEqual:
  case Active
  case Paused
  case Failed
  case Unknown
type DomainEventOutboxRecord = riichinexus.domain.model.DomainEventOutboxRecord
type AuditEventEntry = riichinexus.domain.model.AuditEventEntry
type DomainEventDeliveryReceipt = riichinexus.domain.model.DomainEventDeliveryReceipt
type DomainEventBusSummary = riichinexus.domain.model.DomainEventBusSummary
type DomainEventOutboxBatchOperationResult = riichinexus.domain.model.DomainEventOutboxBatchOperationResult
type DomainEventSubscriberStatus = riichinexus.domain.model.DomainEventSubscriberStatus
type DomainEventSubscriberPartitionStatus = riichinexus.domain.model.DomainEventSubscriberPartitionStatus
type EventCascadeRecord = riichinexus.domain.model.EventCascadeRecord

final case class DomainEventOutboxHistoryView(
    record: DomainEventOutboxRecord,
    auditTrail: Vector[AuditEventEntry],
    deliveryReceipts: Vector[DomainEventDeliveryReceipt]
) derives CanEqual

object DomainEventResponses:
  type DomainEventBusSummaryResponse = DomainEventBusSummary
  type DomainEventOutboxRecordResponse = DomainEventOutboxRecord
  type DomainEventOutboxHistoryResponse = DomainEventOutboxHistoryView
  type DomainEventBatchOperationResponse = DomainEventOutboxBatchOperationResult
  type DomainEventSubscriberStatusResponse = DomainEventSubscriberStatus
  type DomainEventSubscriberPartitionStatusResponse = DomainEventSubscriberPartitionStatus
  type EventCascadeRecordResponse = EventCascadeRecord

  given ReadWriter[DomainEventOutboxHistoryView] = macroRW
