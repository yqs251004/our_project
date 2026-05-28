package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.tournament.objects.{
  AdvancementRuleView,
  KnockoutRuleConfigView,
  StageStatus as ApiStageStatus,
  SwissRuleConfigView,
  TournamentFormat
}

final case class TournamentOperationsStageView(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: ApiStageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None,
    lineupSubmissions: Vector[TournamentLineupSubmissionView]
) derives CanEqual

object TournamentOperationsStageView:
  given ReadWriter[TournamentOperationsStageView] = macroRW

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
      knockoutRule: Option[KnockoutRuleConfig],
      lineupSubmissions: Vector[TournamentLineupSubmissionView]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stageId.value,
      name = name,
      format = TournamentFormat.fromStageFormat(format),
      order = order,
      status = ApiStageStatus.fromDomain(status),
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain),
      lineupSubmissions = lineupSubmissions
    )
