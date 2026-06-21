package riichinexus.microservices.audit.domain.auditevent

import java.time.Instant

import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 记录一次关键业务变更或后台操作的审计事件。
  *
  * 事件通过聚合类型与聚合 ID 指向被操作对象，并保存事件类型、发生时间、可选操作者、结构化详情和备注，供内部审计与故障复盘使用。
  */
final case class AuditEvent(
    id: AuditEventId,
    aggregateType: String,
    aggregateId: String,
    eventType: AuditEventType,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
