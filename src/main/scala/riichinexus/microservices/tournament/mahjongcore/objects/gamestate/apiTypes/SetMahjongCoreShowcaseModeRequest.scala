package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** SetMahjongCoreShowcaseModeRequest 表示Set麻将核心演示模式请求 的前端请求参数。 */

final case class SetMahjongCoreShowcaseModeRequest(enabled: Boolean)

object SetMahjongCoreShowcaseModeRequest:
  given ReadWriter[SetMahjongCoreShowcaseModeRequest] = macroRW
