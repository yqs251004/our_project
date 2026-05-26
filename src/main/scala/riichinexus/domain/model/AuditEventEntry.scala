package riichinexus.domain.model

import java.time.Instant

final case class AuditEventEntry(
    id: AuditEventId,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
) derives CanEqual
