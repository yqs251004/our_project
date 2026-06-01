package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import riichinexus.domain.model.{IdGenerator, MatchRecordId}
import riichinexus.microservices.opsanalytics.objects.{
  AdvancedStatsRecomputeTask,
  AdvancedStatsRecomputeTaskStatus,
  DashboardOwner
}

object AdvancedStatsRecomputeTaskFunctions:
  def create(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      calculatorVersion: Int = AdvancedStatsBoardFunctions.currentCalculatorVersion,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    validate(
      AdvancedStatsRecomputeTask(
        id = IdGenerator.advancedStatsRecomputeTaskId(),
        owner = owner,
        reason = reason,
        calculatorVersion = calculatorVersion,
        requestedAt = requestedAt,
        status = AdvancedStatsRecomputeTaskStatus.Pending,
        lastMatchRecordId = lastMatchRecordId
      )
    )

  def validate(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    if task.reason.trim.isEmpty then
      throw IllegalArgumentException("Advanced stats recompute reason cannot be empty")
    if task.attempts < 0 then
      throw IllegalArgumentException("Advanced stats recompute attempts cannot be negative")
    task

  def isRunnable(task: AdvancedStatsRecomputeTask, asOf: Instant): Boolean =
    task.status == AdvancedStatsRecomputeTaskStatus.Pending &&
      task.nextAttemptAt.forall(!_.isAfter(asOf))

  def markProcessing(task: AdvancedStatsRecomputeTask, at: Instant): AdvancedStatsRecomputeTask =
    validate(task.copy(
      status = AdvancedStatsRecomputeTaskStatus.Processing,
      attempts = task.attempts + 1,
      nextAttemptAt = None,
      startedAt = Some(at),
      completedAt = None,
      deadLetteredAt = None,
      lastError = None
    ))

  def markCompleted(task: AdvancedStatsRecomputeTask, at: Instant): AdvancedStatsRecomputeTask =
    validate(task.copy(
      status = AdvancedStatsRecomputeTaskStatus.Completed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = None
    ))

  def markRetryScheduled(
      task: AdvancedStatsRecomputeTask,
      error: String,
      failedAt: Instant,
      retryAt: Instant
  ): AdvancedStatsRecomputeTask =
    validate(task.copy(
      status = AdvancedStatsRecomputeTaskStatus.Pending,
      nextAttemptAt = Some(retryAt),
      completedAt = Some(failedAt),
      deadLetteredAt = None,
      lastError = Some(error)
    ))

  def markFailed(task: AdvancedStatsRecomputeTask, error: String, at: Instant): AdvancedStatsRecomputeTask =
    validate(task.copy(
      status = AdvancedStatsRecomputeTaskStatus.Failed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = Some(error)
    ))

  def markDeadLetter(task: AdvancedStatsRecomputeTask, error: String, at: Instant): AdvancedStatsRecomputeTask =
    validate(task.copy(
      status = AdvancedStatsRecomputeTaskStatus.DeadLetter,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = Some(at),
      lastError = Some(error)
    ))
