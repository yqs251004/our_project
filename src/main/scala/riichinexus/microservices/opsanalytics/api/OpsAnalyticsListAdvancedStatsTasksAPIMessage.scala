package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTask]] =
    IO {
      requireOpsAdmin(context, operatorId)
      val tasks = context.support.opsAnalyticsModule.tables.listAdvancedStatsTasks()
        .filter(task => status.forall(_ == task.status))
      paged(tasks, Map.from(status.map(value => "status" -> value.toString)))
    }

  private def requireOpsAdmin(context: ApiPlanContext, operatorId: PlayerId): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def paged(items: Vector[AdvancedStatsRecomputeTask], appliedFilters: Map[String, String]): PagedResponse[AdvancedStatsRecomputeTask] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)
