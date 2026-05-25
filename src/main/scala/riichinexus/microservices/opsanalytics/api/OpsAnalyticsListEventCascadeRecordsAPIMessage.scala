package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{EventCascadeRecord as EventCascadeRecordResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.EventCascadeRecordsQuery
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListEventCascadeRecordsAPIMessage(
    query: EventCascadeRecordsQuery
) extends APIMessage[PagedResponse[EventCascadeRecordResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[EventCascadeRecordResponse]] =
    for
      operator <- IO(context.support.principal(query.operatorId))
      _ <- IO(requireOpsAdmin(context, operator))
      resolved <- IO(resolveQuery)
      records <- IO(listCascadeRecords(context, resolved))
    yield paged(records, resolved)

  private def resolveQuery: ResolvedEventCascadeRecordsQuery =
    ResolvedEventCascadeRecordsQuery(
      eventType = query.eventType.filter(_.nonEmpty),
      aggregateType = query.aggregateType.filter(_.nonEmpty),
      aggregateId = query.aggregateId.filter(_.nonEmpty),
      appliedFilters = Vector(
        query.status.map(value => "status" -> value.toString),
        query.consumer.map(value => "consumer" -> value.toString),
        query.eventType.filter(_.nonEmpty).map("eventType" -> _),
        query.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
        query.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _)
      ).flatten.toMap
    )

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)

  private def listCascadeRecords(
      context: ApiPlanContext,
      resolved: ResolvedEventCascadeRecordsQuery
  ): Vector[EventCascadeRecord] =
    context.support.opsAnalyticsModule.tables.listEventCascadeRecords()
      .filter(record => query.status.forall(_ == record.status))
      .filter(record => query.consumer.forall(_ == record.consumer))
      .filter(record => resolved.eventType.forall(_ == record.eventType))
      .filter(record => resolved.aggregateType.forall(_ == record.aggregateType))
      .filter(record => resolved.aggregateId.forall(_ == record.aggregateId))
      .sortBy(record => (record.occurredAt, record.id.value))

  private def paged(
      items: Vector[EventCascadeRecord],
      resolved: ResolvedEventCascadeRecordsQuery
  ): PagedResponse[EventCascadeRecordResponse] =
    val resolvedLimit = query.limit.getOrElse(20)
    val resolvedOffset = query.offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page.map(EventCascadeRecordResponse.fromDomain), items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, resolved.appliedFilters)

  private final case class ResolvedEventCascadeRecordsQuery(
      eventType: Option[String],
      aggregateType: Option[String],
      aggregateId: Option[String],
      appliedFilters: Map[String, String]
  )
