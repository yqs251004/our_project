package riichinexus.microservices.tournament.objects.stage.apiTypes

import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentFormat
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset

/** TournamentStageSummaryView 表示赛事阶段摘要视图 的前端展示视图，包含阶段 ID、名称、format、order、状态、currentRound等。 */

final case class TournamentStageSummaryView(
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
    mahjongRuleset: MahjongRuleset = MahjongRuleset()
)

object TournamentStageSummaryView:
  given ReadWriter[TournamentStageSummaryView] = macroRW

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
      mahjongRuleset: MahjongRuleset
  ): TournamentStageSummaryView =
    TournamentStageSummaryView(
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
      mahjongRuleset = mahjongRuleset
    )
