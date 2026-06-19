package riichinexus.microservices.tournament.domain.stage.model


import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStageId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat}
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig

import riichinexus.system.json.JsonCodecs.given
/** TournamentStage 表示后端领域中的赛事阶段状态或规则，包含 ID、名称、format、order、roundCount、currentRound等。 */
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
    mahjongRuleset: MahjongRuleset = MahjongRuleset(),
    schedulingPoolSize: Int = 4,
    lineupSubmissions: Vector[StageLineupSubmission] = Vector.empty,
    pendingTablePlans: Vector[StageTablePlan] = Vector.empty,
    scheduledTableIds: Vector[TableId] = Vector.empty
)