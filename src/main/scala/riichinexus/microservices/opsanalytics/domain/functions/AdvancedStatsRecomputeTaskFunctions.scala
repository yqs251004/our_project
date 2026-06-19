package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.opsanalytics.objects.{
  AdvancedStatsRecomputeTask,
  AdvancedStatsRecomputeTaskStatus,
  DashboardOwner
}

private[opsanalytics] object AdvancedStatsRecomputeTaskFunctions:
  def create(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      calculatorVersion: Int = AdvancedStatsBoardFunctions.currentCalculatorVersion,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    validate(
      AdvancedStatsRecomputeTask(
        id = OpsAnalyticsIdGenerator.advancedStatsRecomputeTaskId(),
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
