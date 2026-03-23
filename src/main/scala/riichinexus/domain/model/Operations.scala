package riichinexus.domain.model

import java.time.Instant

enum EventCascadeConsumer derives CanEqual:
  case ModerationInbox
  case SettlementExport
  case ProjectionRepair
  case Notification

enum EventCascadeStatus derives CanEqual:
  case Pending
  case Completed

enum DomainEventOutboxStatus derives CanEqual:
  case Pending
  case Processing
  case Completed
  case DeadLetter

final case class EventCascadeRecord(
    id: EventCascadeRecordId,
    eventType: String,
    consumer: EventCascadeConsumer,
    status: EventCascadeStatus,
    aggregateType: String,
    aggregateId: String,
    summary: String,
    occurredAt: Instant,
    handledAt: Option[Instant] = None,
    metadata: Map[String, String] = Map.empty,
    version: Int = 0
) derives CanEqual:
  require(eventType.trim.nonEmpty, "Event cascade eventType cannot be empty")
  require(aggregateType.trim.nonEmpty, "Event cascade aggregateType cannot be empty")
  require(aggregateId.trim.nonEmpty, "Event cascade aggregateId cannot be empty")
  require(summary.trim.nonEmpty, "Event cascade summary cannot be empty")

  def markCompleted(
      at: Instant,
      additionalMetadata: Map[String, String] = Map.empty
  ): EventCascadeRecord =
    copy(
      status = EventCascadeStatus.Completed,
      handledAt = Some(at),
      metadata = metadata ++ additionalMetadata
    )

object EventCascadeRecord:
  def pending(
      eventType: String,
      consumer: EventCascadeConsumer,
      aggregateType: String,
      aggregateId: String,
      summary: String,
      occurredAt: Instant,
      metadata: Map[String, String] = Map.empty
  ): EventCascadeRecord =
    EventCascadeRecord(
      id = IdGenerator.eventCascadeRecordId(),
      eventType = eventType,
      consumer = consumer,
      status = EventCascadeStatus.Pending,
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      summary = summary,
      occurredAt = occurredAt,
      metadata = metadata
    )

  def completed(
      eventType: String,
      consumer: EventCascadeConsumer,
      aggregateType: String,
      aggregateId: String,
      summary: String,
      occurredAt: Instant,
      handledAt: Instant,
      metadata: Map[String, String] = Map.empty
  ): EventCascadeRecord =
    pending(
      eventType = eventType,
      consumer = consumer,
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      summary = summary,
      occurredAt = occurredAt,
      metadata = metadata
    ).markCompleted(handledAt)

final case class DomainEventOutboxRecord(
    id: DomainEventOutboxRecordId,
    eventType: String,
    payload: String,
    occurredAt: Instant,
    status: DomainEventOutboxStatus,
    attempts: Int = 0,
    availableAt: Instant,
    claimedAt: Option[Instant] = None,
    processedAt: Option[Instant] = None,
    lastError: Option[String] = None,
    version: Int = 0
) derives CanEqual:
  require(eventType.trim.nonEmpty, "Domain event outbox eventType cannot be empty")
  require(payload.trim.nonEmpty, "Domain event outbox payload cannot be empty")
  require(attempts >= 0, "Domain event outbox attempts cannot be negative")
  require(!availableAt.isBefore(occurredAt), "Domain event outbox availableAt cannot be earlier than occurredAt")

  def isRunnable(asOf: Instant): Boolean =
    status == DomainEventOutboxStatus.Pending && !availableAt.isAfter(asOf)

  def markProcessing(at: Instant): DomainEventOutboxRecord =
    copy(
      status = DomainEventOutboxStatus.Processing,
      attempts = attempts + 1,
      claimedAt = Some(at),
      processedAt = None,
      lastError = None
    )

  def markCompleted(at: Instant): DomainEventOutboxRecord =
    copy(
      status = DomainEventOutboxStatus.Completed,
      processedAt = Some(at),
      lastError = None
    )

  def markRetryScheduled(error: String, availableAt: Instant): DomainEventOutboxRecord =
    copy(
      status = DomainEventOutboxStatus.Pending,
      availableAt = availableAt,
      processedAt = None,
      lastError = Some(error)
    )

  def markDeadLetter(error: String, at: Instant): DomainEventOutboxRecord =
    copy(
      status = DomainEventOutboxStatus.DeadLetter,
      processedAt = Some(at),
      lastError = Some(error)
    )

object DomainEventOutboxRecord:
  def pending(
      eventType: String,
      payload: String,
      occurredAt: Instant,
      availableAt: Instant
  ): DomainEventOutboxRecord =
    DomainEventOutboxRecord(
      id = IdGenerator.domainEventOutboxRecordId(),
      eventType = eventType,
      payload = payload,
      occurredAt = occurredAt,
      status = DomainEventOutboxStatus.Pending,
      availableAt = availableAt
    )
