package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.system.json.JsonCodecs.given

/** 后端初始化实时麻将状态时使用的请求，可覆盖默认规则并传入可复现的洗牌种子。 */
final case class InitializeMahjongTableStateRequest(
    operatorId: Option[String] = None,
    ruleset: Option[MahjongRuleset] = None,
    seed: Option[String] = None
)

