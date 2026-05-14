package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.domain.model.{AdvancedStatsRecomputeTaskStatus, DomainEventOutboxStatus, EventCascadeConsumer, EventCascadeStatus}

final case class AdvancedStatsTaskQuery(
    status: Option[AdvancedStatsRecomputeTaskStatus] = None
)

final case class DomainEventOutboxQuery(
    asOf: Instant,
    status: Option[DomainEventOutboxStatus] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    subscriberId: Option[String] = None,
    partitionKey: Option[String] = None,
    delivered: Option[Boolean] = None,
    blockedOnly: Boolean = false
)

final case class EventCascadeRecordQuery(
    status: Option[EventCascadeStatus] = None,
    consumer: Option[EventCascadeConsumer] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None
)
