package riichinexus.microservices.audit.domain.functions

import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView

/** AuditEventPrivateViewFunctions 将审计事件领域模型转换为后端内部 private view。 */
private[audit] object AuditEventPrivateViewFunctions:
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
