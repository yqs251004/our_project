package riichinexus.microservices.opsanalytics.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DomainEventOutboxQuery(
    operatorId: PlayerId,
    asOf: Option[Instant] = None,
    status: Option[DomainEventOutboxStatus] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    subscriberId: Option[String] = None,
    partitionKey: Option[String] = None,
    delivered: Option[Boolean] = None,
    blockedOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

final case class DomainEventSubscribersQuery(
    operatorId: PlayerId,
    asOf: Option[Instant] = None,
    subscriberId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

final case class DomainEventSubscriberPartitionsQuery(
    operatorId: PlayerId,
    subscriberId: String,
    asOf: Option[Instant] = None,
    lagOnly: Option[Boolean] = None,
    blockedOnly: Option[Boolean] = None,
    partitionKey: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

final case class EventCascadeRecordsQuery(
    operatorId: PlayerId,
    status: Option[EventCascadeStatus] = None,
    consumer: Option[EventCascadeConsumer] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

final case class AuditEventQuery(
    operatorId: PlayerId,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    actorId: Option[PlayerId] = None,
    eventType: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

final case class AggregateAuditEventQuery(
    operatorId: PlayerId,
    aggregateType: String,
    aggregateId: String,
    actorId: Option[PlayerId] = None,
    eventType: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter:
  def toAuditEventQuery: AuditEventQuery =
    AuditEventQuery(
      operatorId = operatorId,
      aggregateType = Some(aggregateType),
      aggregateId = Some(aggregateId),
      actorId = actorId,
      eventType = eventType,
      limit = limit,
      offset = offset
    )

final case class AdvancedStatsRecomputeRequest(
    operatorId: PlayerId,
    mode: AdvancedStatsBackfillMode = AdvancedStatsBackfillMode.Full,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
):
  require(ownerType.nonEmpty == ownerId.nonEmpty, "ownerType and ownerId must be provided together")
  require(limit > 0, "Advanced stats recompute limit must be positive")

  def targetOwner: Option[DashboardOwner] =
    (ownerType, ownerId) match
      case (Some("player"), Some(id)) => Some(DashboardOwner.Player(PlayerId(id)))
      case (Some("club"), Some(id))   => Some(DashboardOwner.Club(ClubId(id)))
      case (Some(other), Some(_))     => throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
      case _                          => None

  def targetedReason: String =
    reason.getOrElse("manual-targeted-recompute")

  def fullReason: String =
    reason.getOrElse("manual-full-recompute")

  def backfillReason: String =
    reason.getOrElse(s"manual-${mode.toString.toLowerCase}-backfill")

object AdvancedStatsRecomputeRequest:
  given ReadWriter[AdvancedStatsRecomputeRequest] = macroRW
