package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出高级统计重算任务。 */
final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTask]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTask]] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireOpsAdmin(context, operator)
      appliedFilters = Map.from(status.map(value =>
        QueryFilterField.toString(QueryFilterField.Status) -> AdvancedStatsRecomputeTaskStatus.toString(value)
      ))
      tasks <- IO.blocking(listTasks(context, status))
    yield paged(tasks, appliedFilters)

  private def listTasks(
      context: ApiPlanContext,
      status: Option[AdvancedStatsRecomputeTaskStatus]
  ): Vector[AdvancedStatsRecomputeTask] =
    AdvancedStatsRecomputeTaskTable
      .findAll(context.connection)
      .filter(task => status.forall(_ == task.status))

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
      appliedFilters: Map[String, String]
  ): PagedResponse[AdvancedStatsRecomputeTask] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    if resolvedLimit <= 0 then throw IllegalArgumentException("Input field limit must be positive")
    if resolvedOffset < 0 then throw IllegalArgumentException("Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)
