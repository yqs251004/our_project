package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{AuditEventEntry as AuditEventEntryResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.{AggregateAuditEventQuery, AuditEventQuery}
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAggregateAuditsAPIMessage(
    query: AggregateAuditEventQuery
) extends APIMessage[PagedResponse[AuditEventEntryResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AuditEventEntryResponse]] =
    for
      operator <- IO(context.support.principal(query.operatorId))
      _ <- IO(requireAuditPermission(context, operator))
      resolved <- IO(resolveQuery(query.toAuditEventQuery))
      audits <- IO(listAudits(context, resolved))
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      resolved.appliedFilters
    )(AuditEventEntryResponse.fromDomain)

  private def requireAuditPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ViewAuditTrail)

  private def resolveQuery(query: AuditEventQuery): ResolvedAuditQuery =
    ResolvedAuditQuery(
      aggregateType = query.aggregateType.filter(_.nonEmpty),
      aggregateId = query.aggregateId.filter(_.nonEmpty),
      actorId = query.actorId,
      eventType = query.eventType.filter(_.nonEmpty),
      appliedFilters = Vector(
        query.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
        query.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _),
        query.actorId.map(value => "actorId" -> value.value),
        query.eventType.filter(_.nonEmpty).map("eventType" -> _),
        Some("operatorId" -> query.operatorId.value)
      ).flatten.toMap
    )

  private def listAudits(context: ApiPlanContext, query: ResolvedAuditQuery): Vector[AuditEventEntry] =
    context.support.opsAnalyticsModule.tables.listAuditEvents(
      aggregateType = query.aggregateType,
      aggregateId = query.aggregateId,
      actorId = query.actorId,
      eventType = query.eventType
    )

  private final case class ResolvedAuditQuery(
      aggregateType: Option[String],
      aggregateId: Option[String],
      actorId: Option[PlayerId],
      eventType: Option[String],
      appliedFilters: Map[String, String]
  )
