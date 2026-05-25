package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.domain.model.{
  AdvancedStatsBoard as DomainAdvancedStatsBoard,
  AdvancedStatsRecomputeTask as DomainAdvancedStatsRecomputeTask,
  AdvancedStatsTaskQueueSummary as DomainAdvancedStatsTaskQueueSummary,
  Dashboard as DomainDashboard,
  DashboardOwner as DomainDashboardOwner
}
import upickle.default.*

type DashboardOwner = String
type AdvancedStatsRecomputeTaskStatus = String
type AdvancedStatsBackfillMode = String

object DashboardOwner:
  def fromDomain(owner: DomainDashboardOwner): DashboardOwner =
    owner match
      case DomainDashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DomainDashboardOwner.Club(clubId)     => s"club:${clubId.value}"

final case class Dashboard(
    owner: DashboardOwner,
    sampleSize: Int,
    dealInRate: Double,
    winRate: Double,
    averageWinPoints: Double,
    riichiRate: Double,
    averagePlacement: Double,
    topFinishRate: Double,
    lastUpdatedAt: String,
    version: Int
) derives ReadWriter

object Dashboard:
  def fromDomain(dashboard: DomainDashboard): Dashboard =
    Dashboard(
      owner = DashboardOwner.fromDomain(dashboard.owner),
      sampleSize = dashboard.sampleSize,
      dealInRate = dashboard.dealInRate,
      winRate = dashboard.winRate,
      averageWinPoints = dashboard.averageWinPoints,
      riichiRate = dashboard.riichiRate,
      averagePlacement = dashboard.averagePlacement,
      topFinishRate = dashboard.topFinishRate,
      lastUpdatedAt = dashboard.lastUpdatedAt.toString,
      version = dashboard.version
    )

final case class AdvancedStatsBoard(
    owner: DashboardOwner,
    sampleSize: Int,
    defenseStability: Double,
    ukeireExpectation: Double,
    averageShantenImprovement: Double,
    callAggressionRate: Double,
    riichiConversionRate: Double,
    pressureDefenseRate: Double,
    postRiichiFoldRate: Double,
    shantenTrajectory: Vector[Double],
    calculatorVersion: Int,
    strictRoundSampleSize: Int,
    exactUkeireSampleRate: Double,
    exactDefenseSampleRate: Double,
    lastUpdatedAt: String,
    version: Int
) derives ReadWriter

object AdvancedStatsBoard:
  def fromDomain(board: DomainAdvancedStatsBoard): AdvancedStatsBoard =
    AdvancedStatsBoard(
      owner = DashboardOwner.fromDomain(board.owner),
      sampleSize = board.sampleSize,
      defenseStability = board.defenseStability,
      ukeireExpectation = board.ukeireExpectation,
      averageShantenImprovement = board.averageShantenImprovement,
      callAggressionRate = board.callAggressionRate,
      riichiConversionRate = board.riichiConversionRate,
      pressureDefenseRate = board.pressureDefenseRate,
      postRiichiFoldRate = board.postRiichiFoldRate,
      shantenTrajectory = board.shantenTrajectory,
      calculatorVersion = board.calculatorVersion,
      strictRoundSampleSize = board.strictRoundSampleSize,
      exactUkeireSampleRate = board.exactUkeireSampleRate,
      exactDefenseSampleRate = board.exactDefenseSampleRate,
      lastUpdatedAt = board.lastUpdatedAt.toString,
      version = board.version
    )

final case class AdvancedStatsRecomputeTask(
    id: String,
    owner: DashboardOwner,
    reason: String,
    calculatorVersion: Int,
    requestedAt: String,
    status: AdvancedStatsRecomputeTaskStatus,
    attempts: Int,
    lastError: Option[String],
    lastMatchRecordId: Option[String],
    nextAttemptAt: Option[String],
    startedAt: Option[String],
    completedAt: Option[String],
    deadLetteredAt: Option[String],
    version: Int
) derives ReadWriter

object AdvancedStatsRecomputeTask:
  def fromDomain(task: DomainAdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    AdvancedStatsRecomputeTask(
      id = task.id.value,
      owner = DashboardOwner.fromDomain(task.owner),
      reason = task.reason,
      calculatorVersion = task.calculatorVersion,
      requestedAt = task.requestedAt.toString,
      status = task.status.toString,
      attempts = task.attempts,
      lastError = task.lastError,
      lastMatchRecordId = task.lastMatchRecordId.map(_.value),
      nextAttemptAt = task.nextAttemptAt.map(_.toString),
      startedAt = task.startedAt.map(_.toString),
      completedAt = task.completedAt.map(_.toString),
      deadLetteredAt = task.deadLetteredAt.map(_.toString),
      version = task.version
    )

final case class AdvancedStatsTaskQueueSummary(
    asOf: String,
    runnablePendingCount: Int,
    scheduledRetryCount: Int,
    processingCount: Int,
    completedCount: Int,
    failedCount: Int,
    deadLetterCount: Int,
    oldestRunnableRequestedAt: Option[String],
    nextScheduledRetryAt: Option[String],
    newestCompletedAt: Option[String]
) derives ReadWriter

object AdvancedStatsTaskQueueSummary:
  def fromDomain(summary: DomainAdvancedStatsTaskQueueSummary): AdvancedStatsTaskQueueSummary =
    AdvancedStatsTaskQueueSummary(
      asOf = summary.asOf.toString,
      runnablePendingCount = summary.runnablePendingCount,
      scheduledRetryCount = summary.scheduledRetryCount,
      processingCount = summary.processingCount,
      completedCount = summary.completedCount,
      failedCount = summary.failedCount,
      deadLetterCount = summary.deadLetterCount,
      oldestRunnableRequestedAt = summary.oldestRunnableRequestedAt.map(_.toString),
      nextScheduledRetryAt = summary.nextScheduledRetryAt.map(_.toString),
      newestCompletedAt = summary.newestCompletedAt.map(_.toString)
    )
