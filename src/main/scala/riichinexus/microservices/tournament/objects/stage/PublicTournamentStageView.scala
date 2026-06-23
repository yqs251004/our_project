package riichinexus.microservices.tournament.objects.stage

import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentFormat
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.stage.lineup.TournamentLineupSubmissionView

import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot
import upickle.default.{ReadWriter, macroRW}

/** 公开赛事详情页展示的阶段完整视图。
  *
  * 它包含阶段进度、牌桌统计、排名、淘汰赛对阵、晋级规则、赛制规则和阵容提交摘要，但不包含后台操作字段。
  */
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
