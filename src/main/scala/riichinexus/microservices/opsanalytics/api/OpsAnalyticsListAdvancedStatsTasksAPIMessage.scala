package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTask]] =
    for
      _ <- IO.blocking(requireOpsAdmin(context, operatorId))
      query = resolveQuery
      tasks <- IO.blocking(listTasks(context, query))
    yield paged(tasks, query)

  private def resolveQuery: AdvancedStatsTasksQuery =
    AdvancedStatsTasksQuery(
      status = status,
      appliedFilters = Map.from(status.map(value => "status" -> AdvancedStatsRecomputeTaskStatus.toString(value)))
    )

  private def listTasks(
      context: ApiPlanContext,
      query: AdvancedStatsTasksQuery
  ): Vector[AdvancedStatsRecomputeTask] =
    AdvancedStatsRecomputeTaskTable
      .findAll(context.connection)
      .filter(task => query.status.forall(_ == task.status))

  private def requireOpsAdmin(context: ApiPlanContext, operatorId: PlayerId): AccessPrincipal =
    val operator = AuthAccessPrincipalResolver.principal(context, operatorId)
    riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions.requirePermission(context.support.authorizationService, operator, Permission.ManagePlatformOperations)
    operator

  private def paged(
      items: Vector[AdvancedStatsRecomputeTask],
      query: AdvancedStatsTasksQuery
  ): PagedResponse[AdvancedStatsRecomputeTask] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    if resolvedLimit <= 0 then throw IllegalArgumentException("Input field limit must be positive")
    if resolvedOffset < 0 then throw IllegalArgumentException("Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, query.appliedFilters)

  private final case class AdvancedStatsTasksQuery(
      status: Option[AdvancedStatsRecomputeTaskStatus],
      appliedFilters: Map[String, String]
  )
