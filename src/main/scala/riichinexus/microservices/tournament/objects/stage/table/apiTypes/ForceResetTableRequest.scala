package riichinexus.microservices.tournament.objects.stage.table.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** ForceResetTableRequest 表示Force重置牌桌请求 的前端请求参数。 */

final case class ForceResetTableRequest(
    operatorId: String,
    note: String
)

object ForceResetTableRequest:
  given ReadWriter[ForceResetTableRequest] = macroRW
