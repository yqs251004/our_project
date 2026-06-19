package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat}
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.TournamentLineupSubmissionView

import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import upickle.default.{ReadWriter, macroRW}

/** PublicTournamentStageView 表示公开赛事阶段视图 的前端展示视图，包含阶段 ID、名称、format、order、状态、currentRound等。 */

final case class PublicTournamentStageView(
    stageId: String,
    name: String,
    format: TournamentFormat,
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
    advancementRule: AdvancementRule = AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured")),
    swissRule: Option[SwissRuleConfig] = None,
    knockoutRule: Option[KnockoutRuleConfig] = None,
    mahjongRuleset: MahjongRuleset = MahjongRuleset(),
    lineupSubmissions: Vector[TournamentLineupSubmissionView] = Vector.empty
)

object PublicTournamentStageView:
  given ReadWriter[PublicTournamentStageView] = macroRW

  def apply(
      stageId: TournamentStageId,
      name: String,
      format: TournamentFormat,
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
      knockoutRule: Option[KnockoutRuleConfig],
      mahjongRuleset: MahjongRuleset,
      lineupSubmissions: Vector[TournamentLineupSubmissionView]
  ): PublicTournamentStageView =
    PublicTournamentStageView(
      stageId = stageId.value,
      name = name,
      format = format,
      order = order,
      status = status,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      tableCount = tableCount,
      archivedTableCount = archivedTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      standings = standings,
      bracket = bracket,
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule,
      mahjongRuleset = mahjongRuleset,
      lineupSubmissions = lineupSubmissions
    )
