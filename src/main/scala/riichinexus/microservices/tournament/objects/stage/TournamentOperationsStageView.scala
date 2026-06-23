package riichinexus.microservices.tournament.objects.stage

import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentFormat
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.stage.lineup.TournamentLineupSubmissionView

/** 赛事运营后台使用的阶段视图。
  *
  * 视图聚焦排桌计划、已排牌桌、阶段规则和阵容提交，供管理员配置阶段、生成牌桌和推进轮次。
  */
final case class TournamentOperationsStageView(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    advancementRule: AdvancementRule = AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured")),
    swissRule: Option[SwissRuleConfig] = None,
    knockoutRule: Option[KnockoutRuleConfig] = None,
    mahjongRuleset: MahjongRuleset = MahjongRuleset(),
    lineupSubmissions: Vector[TournamentLineupSubmissionView] = Vector.empty
)

object TournamentOperationsStageView:
  given ReadWriter[TournamentOperationsStageView] = macroRW

  def apply(
      stageId: TournamentStageId,
      name: String,
      format: TournamentFormat,
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
      mahjongRuleset: MahjongRuleset,
      lineupSubmissions: Vector[TournamentLineupSubmissionView]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stageId.value,
      name = name,
      format = format,
      order = order,
      status = status,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount,
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule,
      mahjongRuleset = mahjongRuleset,
      lineupSubmissions = lineupSubmissions
    )
