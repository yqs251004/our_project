package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 切换麻将核心演示模式的请求体。
  *
  * 该开关面向开发和展示场景，生产玩法逻辑仍以真实牌桌状态为准。
  */
final case class SetMahjongCoreShowcaseModeRequest(enabled: Boolean)

object SetMahjongCoreShowcaseModeRequest:
  given ReadWriter[SetMahjongCoreShowcaseModeRequest] = macroRW
