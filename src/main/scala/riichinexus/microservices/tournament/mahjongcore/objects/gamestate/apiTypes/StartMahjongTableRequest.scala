package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 请求启动某张比赛桌的实时麻将对局，可覆盖默认规则并传入可复现的洗牌种子�?*/
final case class StartMahjongTableRequest(
    operatorId: Option[String] = None,
    ruleset: Option[MahjongRuleset] = None,
    seed: Option[String] = None
)

object StartMahjongTableRequest:
  given ReadWriter[StartMahjongTableRequest] = macroRW
