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
    metadata: Map[String, String] = Map.empty
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
