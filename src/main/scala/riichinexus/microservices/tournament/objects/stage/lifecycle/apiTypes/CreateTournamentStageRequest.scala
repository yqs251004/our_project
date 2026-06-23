package riichinexus.microservices.tournament.objects.stage.lifecycle.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentFormat
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutSeedingPolicy
import riichinexus.microservices.tournament.objects.stage.rules.progression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissPairingMethod
import upickle.default.{ReadWriter, macroRW}

/** 创建赛事阶段时提交的赛制、晋级、排桌和麻将规则配置。
  *
  * 请求覆盖瑞士轮、淘汰赛、晋级规则、调度池和规则模板等可选参数，后端会按阶段赛制读取相应字段。
  */
final case class CreateTournamentStageRequest(
    name: String,
    format: TournamentFormat,
    order: Int,
    roundCount: Int,
    operatorId: Option[String] = None,
    ruleTemplateKey: Option[String] = None,
    advancementRuleType: Option[AdvancementRuleType] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    note: Option[String] = None,
    pairingMethod: Option[SwissPairingMethod] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[KnockoutSeedingPolicy] = None,
    mahjongRuleset: Option[MahjongRuleset] = None,
    schedulingPoolSize: Option[Int] = None
)

object CreateTournamentStageRequest:
  given ReadWriter[CreateTournamentStageRequest] = macroRW
