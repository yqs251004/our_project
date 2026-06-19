package riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat
import upickle.default.{ReadWriter, macroRW}

/** CreateTournamentStageRequest 表示创建赛事阶段请求 的前端请求参数。 */

final case class CreateTournamentStageRequest(
    name: String,
    format: TournamentFormat,
    order: Int,
    roundCount: Int,
    operatorId: Option[String] = None,
    ruleTemplateKey: Option[String] = None,
    advancementRuleType: Option[String] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    note: Option[String] = None,
    pairingMethod: Option[String] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[String] = None,
    mahjongRuleset: Option[MahjongRuleset] = None,
    schedulingPoolSize: Option[Int] = None
)

object CreateTournamentStageRequest:
  given ReadWriter[CreateTournamentStageRequest] = macroRW
