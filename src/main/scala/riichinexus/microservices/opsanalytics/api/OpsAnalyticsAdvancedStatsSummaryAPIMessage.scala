package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsAdvancedStatsSummaryAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None
) extends APIMessage[AdvancedStatsTaskQueueSummary] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsTaskQueueSummary] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      val resolvedAsOf = asOf.getOrElse(Instant.now())
      val tasks = context.support.opsAnalyticsModule.advancedStatsRecomputeTaskRepository.findAll()
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
    }
