package riichinexus.microservices.audit.domain.auditevent

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AuditEvent 表示后端领域中的审计事件状态或规则，包含 ID、aggregateType、aggregateId、eventType、occurredAt、actorId等。 */

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
