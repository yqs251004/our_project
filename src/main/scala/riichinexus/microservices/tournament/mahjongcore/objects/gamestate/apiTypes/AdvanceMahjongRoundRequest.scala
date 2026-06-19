package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AdvanceMahjongRoundRequest 表示Advance麻将小局请求 的前端请求参数。 */

final case class AdvanceMahjongRoundRequest(
    playerId: Option[String] = None
)

object AdvanceMahjongRoundRequest:
  given ReadWriter[AdvanceMahjongRoundRequest] = macroRW
