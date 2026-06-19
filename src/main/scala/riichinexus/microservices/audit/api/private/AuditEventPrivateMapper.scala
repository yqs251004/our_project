package riichinexus.microservices.audit.api.`private`

import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView

/** AuditEventPrivateMapper 供后端服务执行审计事件后端内部Mapper 流程，避免其它微服务直接访问内部表或领域模型。 */

private[audit] object AuditEventPrivateMapper:
  def toPrivateView(event: AuditEvent): AuditEventPrivateView =
    AuditEventPrivateView(
      id = event.id.value,
      aggregateType = event.aggregateType,
      aggregateId = event.aggregateId,
      eventType = event.eventType,
      occurredAt = event.occurredAt,
      actorId = event.actorId,
      details = event.details,
      note = event.note
    )
