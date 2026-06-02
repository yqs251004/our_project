package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.*

/** 请求重置某张实时麻将桌的状态，通常由运营或申诉流程触发。 */
final case class ResetMahjongTableRequest(
    operatorId: Option[String] = None,
    note: String
)

object ResetMahjongTableRequest:
  given ReadWriter[ResetMahjongTableRequest] = macroRW
