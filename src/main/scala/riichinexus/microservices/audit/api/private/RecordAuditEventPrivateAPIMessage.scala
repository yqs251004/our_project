package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.audit.tables.auditevent.AuditEventTable
import riichinexus.system.realtime.domain.AuditRealtimeMapper
import upickle.default.*

final case class RecordAuditEventPrivateAPIMessage(
    event: AuditEvent
) extends APIMessage[AuditEvent] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuditEvent] =
    for
      saved <- IO.blocking(AuditEventTable.save(context.connection, event))
      _ <- context.realtimeEventBus.publish(AuditRealtimeMapper.fromAudit(saved))
    yield saved
