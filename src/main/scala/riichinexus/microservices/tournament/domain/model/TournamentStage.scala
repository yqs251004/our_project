package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{AdvancementRuleType, StageStatus, TournamentFormat}

final case class TournamentStage(
    id: TournamentStageId,
    name: String,
    format: TournamentFormat,
    order: Int,
    roundCount: Int,
    currentRound: Int = 1,
    status: StageStatus = StageStatus.Pending,
    advancementRule: AdvancementRule = AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured")),
    swissRule: Option[SwissRuleConfig] = None,
    knockoutRule: Option[KnockoutRuleConfig] = None,
    schedulingPoolSize: Int = 4,
    lineupSubmissions: Vector[StageLineupSubmission] = Vector.empty,
    pendingTablePlans: Vector[StageTablePlan] = Vector.empty,
    scheduledTableIds: Vector[TableId] = Vector.empty
) derives CanEqual:
  require(order >= 1, "Stage order must be positive")
  require(roundCount >= 1, "Stage round count must be positive")
  require(currentRound >= 1 && currentRound <= roundCount, "Current round must be within stage bounds")
  require(schedulingPoolSize >= 1, "Scheduling pool size must be positive")

  def withRules(
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Int
  ): TournamentStage =
    require(schedulingPoolSize >= 1, "Scheduling pool size must be positive")
    copy(
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule,
      schedulingPoolSize = schedulingPoolSize
    )

  def submitLineup(submission: StageLineupSubmission): TournamentStage =
    require(
      status != StageStatus.Completed && status != StageStatus.Archived,
      "Cannot submit lineups to a completed stage"
    )
    copy(
      status = StageStatus.Ready,
      lineupSubmissions =
        lineupSubmissions.filterNot(_.clubId == submission.clubId) :+ submission
    )

  def queueRoundPlans(
      roundNumber: Int,
      plans: Vector[StageTablePlan]
  ): TournamentStage =
    require(roundNumber >= 1 && roundNumber <= roundCount, "Round number is out of bounds")
    require(plans.forall(_.roundNumber == roundNumber), "Queued plans must share the same round number")
    copy(
      currentRound = roundNumber,
      status = StageStatus.Active,
      pendingTablePlans = plans
    )

  def consumePendingPlans(
      materializedPlans: Vector[StageTablePlan],
      tableIds: Vector[TableId]
  ): TournamentStage =
    require(
      materializedPlans.size == tableIds.size,
      "Materialized plans and table ids must have the same size"
    )
    val consumedKeys = materializedPlans.map(plan => plan.roundNumber -> plan.tableNo).toSet
    copy(
      status = StageStatus.Active,
      pendingTablePlans =
        pendingTablePlans.filterNot(plan => consumedKeys.contains(plan.roundNumber -> plan.tableNo)),
      scheduledTableIds = (scheduledTableIds ++ tableIds).distinct
    )

  def advanceRound(nextRound: Int): TournamentStage =
    require(nextRound >= 1 && nextRound <= roundCount, "Next round is out of bounds")
    copy(currentRound = nextRound)

  def registerScheduledTables(tableIds: Vector[TableId]): TournamentStage =
    require(tableIds.nonEmpty, "Scheduled tables cannot be empty")
    copy(
      status = StageStatus.Active,
      scheduledTableIds = (scheduledTableIds ++ tableIds).distinct
    )

  def complete: TournamentStage =
    copy(status = StageStatus.Completed)

