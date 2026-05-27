package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
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
      operator <- IO(context.principal(operatorId))
      _ <- IO(requireOpsAdmin(context, operator))
      resolvedAsOf <- resolveAsOf
      tasks <- IO(AdvancedStatsRecomputeTaskTable.findAll(context.connection))
      summary = buildSummary(tasks, resolvedAsOf)
    yield summary

  private def resolveAsOf: IO[Instant] =
    asOf match
      case Some(value) => IO(value)
      case None        => IO.realTimeInstant

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManagePlatformOperations)

  private def buildSummary(
      tasks: Vector[AdvancedStatsRecomputeTask],
      resolvedAsOf: Instant
  ): AdvancedStatsTaskQueueSummary =
    AdvancedStatsTaskQueueSummary(
      asOf = resolvedAsOf,
      runnablePendingCount = tasks.count(_.isRunnable(resolvedAsOf)),
      scheduledRetryCount = tasks.count(task =>
        task.status == AdvancedStatsRecomputeTaskStatus.Pending &&
          task.nextAttemptAt.exists(_.isAfter(resolvedAsOf))
      ),
      processingCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Processing),
      completedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Completed),
      failedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Failed),
      deadLetterCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter),
      oldestRunnableRequestedAt = tasks.filter(_.isRunnable(resolvedAsOf)).map(_.requestedAt).sorted.headOption,
      nextScheduledRetryAt = tasks
        .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
        .flatMap(_.nextAttemptAt)
        .filter(_.isAfter(resolvedAsOf))
        .sorted
        .headOption,
      newestCompletedAt = tasks.flatMap(_.completedAt).sorted.lastOption
    )
