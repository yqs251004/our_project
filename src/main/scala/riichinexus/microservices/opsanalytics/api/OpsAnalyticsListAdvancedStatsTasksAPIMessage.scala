package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{AdvancedStatsRecomputeTask as AdvancedStatsRecomputeTaskResponse}
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTaskResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTaskResponse]] =
    for
      _ <- IO(requireOpsAdmin(context, operatorId))
      query = resolveQuery
      tasks <- IO(listTasks(context, query))
    yield paged(tasks, query)

  private def resolveQuery: AdvancedStatsTasksQuery =
    AdvancedStatsTasksQuery(
      status = status,
      appliedFilters = Map.from(status.map(value => "status" -> value.toString))
    )

  private def listTasks(
      context: ApiPlanContext,
      query: AdvancedStatsTasksQuery
  ): Vector[AdvancedStatsRecomputeTask] =
    context.support.opsAnalyticsModule.tables
      .listAdvancedStatsTasks()
      .filter(task => query.status.forall(_ == task.status))

  private def requireOpsAdmin(context: ApiPlanContext, operatorId: PlayerId): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def paged(
      items: Vector[AdvancedStatsRecomputeTask],
      query: AdvancedStatsTasksQuery
  ): PagedResponse[AdvancedStatsRecomputeTaskResponse] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page.map(AdvancedStatsRecomputeTaskResponse.fromDomain), items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, query.appliedFilters)

  private final case class AdvancedStatsTasksQuery(
      status: Option[AdvancedStatsRecomputeTaskStatus],
      appliedFilters: Map[String, String]
  )
