package riichinexus.microservices.audit.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.domain.model.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.domain.functions.AuditEventPrivateViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView
import riichinexus.microservices.audit.tables.auditevent.AuditEventTable
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务读取审计事件列表。 */
final case class ListAuditEventsPrivateAPIMessage(
    aggregateType: Option[AggregateType] = None,
    aggregateId: Option[String] = None,
    eventType: Option[AuditEventType] = None,
    oldestFirst: Boolean = false
) extends APIMessage[Vector[AuditEventPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[AuditEventPrivateView]] =
    for
      events <- IO.blocking(listAuditEvents(context))
    yield events.map(AuditEventPrivateViewFunctions.toPrivateView)

  private def listAuditEvents(context: ApiPlanContext): Vector[AuditEvent] =
    (aggregateType, aggregateId, eventType, oldestFirst) match
      case (Some(actualAggregateType), Some(actualAggregateId), Some(actualEventType), true) =>
        AuditEventTable.findByAggregateAndEventTypeOldestFirst(
          context.connection,
          actualAggregateType,
          actualAggregateId,
          actualEventType
        )
      case (Some(actualAggregateType), Some(actualAggregateId), Some(actualEventType), false) =>
        AuditEventTable.findByAggregateAndEventType(
          context.connection,
          actualAggregateType,
          actualAggregateId,
          actualEventType
        )
      case (Some(actualAggregateType), Some(actualAggregateId), None, true) =>
        AuditEventTable.findByAggregateOldestFirst(context.connection, actualAggregateType, actualAggregateId)
      case (Some(actualAggregateType), Some(actualAggregateId), None, false) =>
        AuditEventTable.findByAggregate(context.connection, actualAggregateType, actualAggregateId)
      case (None, None, None, true) =>
        AuditEventTable.findAllOldestFirst(context.connection)
      case (None, None, None, false) =>
        AuditEventTable.findAll(context.connection)
      case _ =>
        throw IllegalArgumentException("Unsupported audit event query filters")
