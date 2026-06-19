package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.functions.AuditEventPrivateViewFunctions
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.objects.`private`.{AuditEventDraft, AuditEventPrivateView}

import riichinexus.microservices.audit.tables.auditevent.AuditEventTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.system.realtime.domain.AuditRealtimeMapper
import upickle.default.ReadWriter

/** 供后端服务记录单个审计事件。 */
final case class RecordAuditEventPrivateAPIMessage(
    event: AuditEventDraft
) extends APIMessage[AuditEventPrivateView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuditEventPrivateView] =
    for
      auditEvent <- IO.blocking(toAuditEvent(event))
      saved <- saveAuditEvent(context, auditEvent)
      _ <- context.realtimeEventBus.publish(AuditRealtimeMapper.fromAudit(saved))
    yield AuditEventPrivateViewFunctions.toPrivateView(saved)

  private def saveAuditEvent(context: ApiPlanContext, auditEvent: AuditEvent): IO[AuditEvent] =
    IO.blocking(AuditEventTable.save(context.connection, auditEvent))

  private def toAuditEvent(draft: AuditEventDraft): AuditEvent =
    AuditEvent(
      id = AuditIdGenerator.auditEventId(),
      aggregateType = draft.aggregateType,
      aggregateId = draft.aggregateId,
      eventType = draft.eventType,
      occurredAt = draft.occurredAt,
      actorId = draft.actorId,
      details = draft.details,
      note = draft.note
    )
