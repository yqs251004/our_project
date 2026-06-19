package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出高级统计重算任务。 */
final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTask]] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireOpsAdmin(context, operator)
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

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ManagePlatformOperations
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to manage platform operations"))
    }

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
