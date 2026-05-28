package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.{
  AdvancementRuleView,
  KnockoutBracketSnapshot,
  KnockoutRuleConfigView,
  StageStatus as ApiStageStatus,
  StageRankingSnapshot,
  SwissRuleConfigView,
  TournamentFormat
}
import upickle.default.*

final case class PublicTournamentStageView(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: ApiStageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    tableCount: Int,
    archivedTableCount: Int,
    pendingTablePlanCount: Int,
    standings: Option[StageRankingSnapshot],
    bracket: Option[KnockoutBracketSnapshot],
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None
) derives CanEqual

object PublicTournamentStageView:
  given ReadWriter[PublicTournamentStageView] = macroRW

  def apply(
      stageId: TournamentStageId,
      name: String,
      format: StageFormat,
      order: Int,
      status: StageStatus,
      currentRound: Int,
      roundCount: Int,
      schedulingPoolSize: Int,
      tableCount: Int,
      archivedTableCount: Int,
      pendingTablePlanCount: Int,
      standings: Option[StageRankingSnapshot],
      bracket: Option[KnockoutBracketSnapshot],
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig]
  ): PublicTournamentStageView =
    PublicTournamentStageView(
      stageId = stageId.value,
      name = name,
      format = TournamentFormat.fromStageFormat(format),
      order = order,
      status = ApiStageStatus.fromDomain(status),
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      tableCount = tableCount,
      archivedTableCount = archivedTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      standings = standings,
      bracket = bracket,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )
