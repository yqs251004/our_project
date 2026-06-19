package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.objects.`private`.{AuditEventDraft, AuditEventPrivateView}

import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端服务批量记录审计事件。 */
final case class RecordAuditEventsPrivateAPIMessage(
    events: Vector[AuditEventDraft]
) extends APIMessage[Vector[AuditEventPrivateView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AuditEventPrivateView]] =
    events.foldLeft(IO.pure(Vector.empty[AuditEventPrivateView])) { (plan, event) =>
      for
        savedEvents <- plan
        savedEvent <- RecordAuditEventPrivateAPIMessage(event).plan(context)
      yield savedEvents :+ savedEvent
    }
