package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 请求实时麻将桌进入下一小局的操作参数。
  *
  * `playerId` 可记录触发推进的玩家，省略时表示由系统或管理员流程推进。
  */
final case class AdvanceMahjongRoundRequest(
    playerId: Option[String] = None
)

object AdvanceMahjongRoundRequest:
  given ReadWriter[AdvanceMahjongRoundRequest] = macroRW
