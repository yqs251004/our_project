package riichinexus.microservices.tournament.objects.stage.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentFormat
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutSeedingPolicy
import riichinexus.microservices.tournament.objects.stage.rules.progression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissPairingMethod
import upickle.default.{ReadWriter, macroRW}

/** ConfigureStageRulesRequest 表示Configure阶段Rules请求 的前端请求参数。 */

final case class ConfigureStageRulesRequest(
    operatorId: String,
    format: Option[TournamentFormat] = None,
    roundCount: Option[Int] = None,
    advancementRuleType: Option[AdvancementRuleType] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Option[Int] = None,
    ruleTemplateKey: Option[String] = None,
    pairingMethod: Option[SwissPairingMethod] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[KnockoutSeedingPolicy] = None,
    mahjongRuleset: Option[MahjongRuleset] = None,
    note: Option[String] = None
)

object ConfigureStageRulesRequest:
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
