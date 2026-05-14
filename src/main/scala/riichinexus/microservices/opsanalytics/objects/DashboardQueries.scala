package riichinexus.microservices.opsanalytics.objects

import riichinexus.domain.model.*

final case class AuditTrailQuery(
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    actorId: Option[PlayerId] = None,
    eventType: Option[String] = None
)
