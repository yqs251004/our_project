package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAuditsAPIMessage(
    operatorId: PlayerId,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    actorId: Option[PlayerId] = None,
    eventType: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AuditEventEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AuditEventEntry]] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ViewAuditTrail)
      val audits = context.support.opsAnalyticsModule.tables.listAuditEvents(
        aggregateType = aggregateType.filter(_.nonEmpty),
        aggregateId = aggregateId.filter(_.nonEmpty),
        actorId = actorId,
        eventType = eventType.filter(_.nonEmpty)
      )
      paged(
        audits,
        Vector(
          aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
          aggregateId.filter(_.nonEmpty).map("aggregateId" -> _),
          actorId.map(value => "actorId" -> value.value),
          eventType.filter(_.nonEmpty).map("eventType" -> _),
          Some("operatorId" -> operatorId.value)
        ).flatten.toMap
      )
    }

  private def paged(items: Vector[AuditEventEntry], appliedFilters: Map[String, String]): PagedResponse[AuditEventEntry] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)
