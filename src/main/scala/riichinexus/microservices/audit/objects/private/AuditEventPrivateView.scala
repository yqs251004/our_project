package riichinexus.microservices.audit.objects.`private`

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AuditEventPrivateView 表示后端内部使用的审计事件后端内部视图 read model，包含 ID、aggregateType、aggregateId、eventType、occurredAt、actorId等。 */

final case class AuditEventPrivateView(
    id: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
