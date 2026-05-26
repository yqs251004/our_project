package riichinexus.bootstrap

import riichinexus.application.ports.*

final case class ApplicationRepositoryContext(
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    domainEventOutboxRepository: DomainEventOutboxRepository,
    domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
    auditEventRepository: AuditEventRepository
)
