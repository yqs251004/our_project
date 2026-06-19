package riichinexus.microservices.audit.objects.`private`

import java.time.Instant

import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AuditEventDraft 表示后端内部提交给私有 API 的审计事件草稿 数据草稿，包含aggregateType、aggregateId、eventType、occurredAt、actorId、details等。 */

final case class AuditEventDraft(
    aggregateType: String,
    aggregateId: String,
    eventType: AuditEventType,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
