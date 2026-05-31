package riichinexus.microservices.tournament.domain.tournamentmanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.domain.model.TableId
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRule
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.tournamentmanagement.StageStatus
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig

object TournamentStageFunctions:
  def validate(stage: TournamentStage): Unit =
    require(stage.order >= 1, "Stage order must be positive")
    require(stage.roundCount >= 1, "Stage round count must be positive")
    require(stage.currentRound >= 1 && stage.currentRound <= stage.roundCount, "Current round must be within stage bounds")
    require(stage.schedulingPoolSize >= 1, "Scheduling pool size must be positive")

  def withRules(
      stage: TournamentStage,
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Int
  ): TournamentStage =
    require(schedulingPoolSize >= 1, "Scheduling pool size must be positive")
    stage.copy(
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule,
      schedulingPoolSize = schedulingPoolSize
    )

  def submitLineup(stage: TournamentStage, submission: StageLineupSubmission): TournamentStage =
    require(
      stage.status != StageStatus.Completed && stage.status != StageStatus.Archived,
      "Cannot submit lineups to a completed stage"
    )
    stage.copy(
      status = StageStatus.Ready,
      lineupSubmissions =
        stage.lineupSubmissions.filterNot(_.clubId == submission.clubId) :+ submission
    )

  def queueRoundPlans(
      stage: TournamentStage,
      roundNumber: Int,
      plans: Vector[StageTablePlan]
  ): TournamentStage =
    require(roundNumber >= 1 && roundNumber <= stage.roundCount, "Round number is out of bounds")
    require(plans.forall(_.roundNumber == roundNumber), "Queued plans must share the same round number")
    stage.copy(
      currentRound = roundNumber,
      status = StageStatus.Active,
      pendingTablePlans = plans
    )

  def consumePendingPlans(
      stage: TournamentStage,
      materializedPlans: Vector[StageTablePlan],
      tableIds: Vector[TableId]
  ): TournamentStage =
    require(
      materializedPlans.size == tableIds.size,
      "Materialized plans and table ids must have the same size"
    )
    val consumedKeys = materializedPlans.map(plan => plan.roundNumber -> plan.tableNo).toSet
    stage.copy(
      status = StageStatus.Active,
      pendingTablePlans =
        stage.pendingTablePlans.filterNot(plan => consumedKeys.contains(plan.roundNumber -> plan.tableNo)),
      scheduledTableIds = (stage.scheduledTableIds ++ tableIds).distinct
    )

  def advanceRound(stage: TournamentStage, nextRound: Int): TournamentStage =
    require(nextRound >= 1 && nextRound <= stage.roundCount, "Next round is out of bounds")
    stage.copy(currentRound = nextRound)

  def registerScheduledTables(stage: TournamentStage, tableIds: Vector[TableId]): TournamentStage =
    require(tableIds.nonEmpty, "Scheduled tables cannot be empty")
    stage.copy(
      status = StageStatus.Active,
      scheduledTableIds = (stage.scheduledTableIds ++ tableIds).distinct
    )

  def complete(stage: TournamentStage): TournamentStage =
    stage.copy(status = StageStatus.Completed)
