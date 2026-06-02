package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.audit.tables.auditevent.AuditEventTable
import upickle.default.*

final case class RecordAuditEventPrivateAPIMessage(
    event: AuditEvent
) extends APIMessage[AuditEvent] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuditEvent] =
    IO.blocking(AuditEventTable.save(context.connection, event))
