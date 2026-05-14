package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.domain.model.*

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

  export DomainEventResponseCodecs.given
