package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsRecomputeTaskFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus, AdvancedStatsTaskQueueSummary}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
/** 获取运营分析高级统计总览。 */
final case class OpsAnalyticsAdvancedStatsSummaryAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None
) extends APIMessage[AdvancedStatsTaskQueueSummary]:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsTaskQueueSummary] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireOpsAdmin(context, operator)
      resolvedAsOf <- resolveAsOf
      tasks <- IO.blocking(AdvancedStatsRecomputeTaskTable.findAll(context.connection))
      summary = buildSummary(tasks, resolvedAsOf)
    yield summary

  private def resolveAsOf: IO[Instant] =
    asOf match
      case Some(value) => IO.pure(value)
      case None        => IO.realTimeInstant

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ManagePlatformOperations
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to manage platform operations"))
    }

  private def buildSummary(
      tasks: Vector[AdvancedStatsRecomputeTask],
      resolvedAsOf: Instant
  ): AdvancedStatsTaskQueueSummary =
    AdvancedStatsTaskQueueSummary(
      asOf = resolvedAsOf,
      runnablePendingCount = tasks.count(AdvancedStatsRecomputeTaskFunctions.isRunnable(_, resolvedAsOf)),
      scheduledRetryCount = tasks.count(task =>
        task.status == AdvancedStatsRecomputeTaskStatus.Pending &&
          task.nextAttemptAt.exists(_.isAfter(resolvedAsOf))
      ),
      processingCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Processing),
      completedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Completed),
      failedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Failed),
      deadLetterCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter),
      oldestRunnableRequestedAt = tasks
        .filter(AdvancedStatsRecomputeTaskFunctions.isRunnable(_, resolvedAsOf))
        .map(_.requestedAt)
        .sorted
        .headOption,
      nextScheduledRetryAt = tasks
        .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
        .flatMap(_.nextAttemptAt)
        .filter(_.isAfter(resolvedAsOf))
        .sorted
        .headOption,
      newestCompletedAt = tasks.flatMap(_.completedAt).sorted.lastOption
    )
