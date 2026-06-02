package riichinexus.microservices.audit.domain.auditevent

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

final case class AuditEvent(
    id: AuditEventId,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
