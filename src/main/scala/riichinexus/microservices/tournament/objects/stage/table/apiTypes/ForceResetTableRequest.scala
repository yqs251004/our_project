package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 管理员强制重置牌桌状态时提交的请求体。
  *
  * 强制重置会影响对局流程和重置计数，因此必须携带操作者和明确原因说明。
  */
final case class ForceResetTableRequest(
    operatorId: String,
    note: String
)

object ForceResetTableRequest:
  given ReadWriter[ForceResetTableRequest] = macroRW
