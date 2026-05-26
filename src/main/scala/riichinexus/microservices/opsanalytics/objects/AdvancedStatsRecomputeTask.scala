package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.domain.model.{AdvancedStatsRecomputeTaskId, IdGenerator, MatchRecordId}

final case class AdvancedStatsRecomputeTask(
    id: AdvancedStatsRecomputeTaskId,
    owner: DashboardOwner,
    reason: String,
    calculatorVersion: Int,
    requestedAt: Instant,
    status: AdvancedStatsRecomputeTaskStatus,
    attempts: Int = 0,
    lastError: Option[String] = None,
    lastMatchRecordId: Option[MatchRecordId] = None,
    nextAttemptAt: Option[Instant] = None,
    startedAt: Option[Instant] = None,
    completedAt: Option[Instant] = None,
    deadLetteredAt: Option[Instant] = None,
    version: Int = 0
) derives CanEqual:
  require(reason.trim.nonEmpty, "Advanced stats recompute reason cannot be empty")
  require(attempts >= 0, "Advanced stats recompute attempts cannot be negative")

  def isRunnable(asOf: Instant): Boolean =
    status == AdvancedStatsRecomputeTaskStatus.Pending &&
      nextAttemptAt.forall(!_.isAfter(asOf))

  def markProcessing(at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Processing,
      attempts = attempts + 1,
      nextAttemptAt = None,
      startedAt = Some(at),
      completedAt = None,
      deadLetteredAt = None,
      lastError = None
    )

  def markCompleted(at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Completed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = None
    )

  def markRetryScheduled(error: String, failedAt: Instant, retryAt: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Pending,
      nextAttemptAt = Some(retryAt),
      completedAt = Some(failedAt),
      deadLetteredAt = None,
      lastError = Some(error)
    )

  def markFailed(error: String, at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Failed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = Some(error)
    )

  def markDeadLetter(error: String, at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.DeadLetter,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = Some(at),
      lastError = Some(error)
    )

object AdvancedStatsRecomputeTask:
  def create(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      calculatorVersion: Int = AdvancedStatsBoard.CurrentCalculatorVersion,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    AdvancedStatsRecomputeTask(
      id = IdGenerator.advancedStatsRecomputeTaskId(),
      owner = owner,
      reason = reason,
      calculatorVersion = calculatorVersion,
      requestedAt = requestedAt,
      status = AdvancedStatsRecomputeTaskStatus.Pending,
      lastMatchRecordId = lastMatchRecordId
    )
