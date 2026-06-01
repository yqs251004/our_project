package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsRecomputeTaskFunctions
import riichinexus.microservices.opsanalytics.objects.{
  AdvancedStatsRecomputeTask,
  AdvancedStatsRecomputeTaskStatus,
  AdvancedStatsTaskQueueSummary
}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import upickle.default.*

final case class OpsAnalyticsAdvancedStatsSummaryAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None
) extends APIMessage[AdvancedStatsTaskQueueSummary] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsTaskQueueSummary] =
    for
      operator <- IO.blocking(AuthAccessPrincipalResolver.principal(context, operatorId))
      _ <- IO.blocking(requireOpsAdmin(context, operator))
      resolvedAsOf <- resolveAsOf
      tasks <- IO.blocking(AdvancedStatsRecomputeTaskTable.findAll(context.connection))
      summary = buildSummary(tasks, resolvedAsOf)
    yield summary

  private def resolveAsOf: IO[Instant] =
    asOf match
      case Some(value) => IO.blocking(value)
      case None        => IO.realTimeInstant

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions.requirePermission(context.support.authorizationService, operator, Permission.ManagePlatformOperations)

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
