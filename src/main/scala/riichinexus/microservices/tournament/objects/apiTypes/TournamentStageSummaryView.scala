package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{
  AdvancementRuleView,
  KnockoutRuleConfigView,
  SwissRuleConfigView,
  TournamentFormat
}

final case class TournamentStageSummaryView(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None
) derives CanEqual

object TournamentStageSummaryView:
  def apply(
      stageId: TournamentStageId,
      name: String,
      format: StageFormat,
      order: Int,
      status: StageStatus,
      currentRound: Int,
      roundCount: Int,
      schedulingPoolSize: Int,
      pendingTablePlanCount: Int,
      scheduledTableCount: Int,
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig]
  ): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stageId.value,
      name = name,
      format = TournamentFormat.fromStageFormat(format),
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )

  def fromDomain(stage: TournamentStage): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stage.id.value,
      name = stage.name,
      format = TournamentFormat.fromStageFormat(stage.format),
      order = stage.order,
      status = stage.status.toString,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      advancementRule = AdvancementRuleView.fromDomain(stage.advancementRule),
      swissRule = stage.swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = stage.knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )
