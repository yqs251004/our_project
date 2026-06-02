package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class RecordAuditEventsPrivateAPIMessage(
    events: Vector[AuditEvent]
) extends APIMessage[Vector[AuditEvent]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AuditEvent]] =
    events.foldLeft(IO.pure(Vector.empty[AuditEvent])) { (plan, event) =>
      for
        savedEvents <- plan
        savedEvent <- RecordAuditEventPrivateAPIMessage(event).plan(context)
      yield savedEvents :+ savedEvent
    }
