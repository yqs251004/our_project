package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 管理员更新某个牌桌座位状态的请求体。
  *
  * 可局部修改准备或断线状态，`operatorId` 与备注用于记录后台介入原因。
  */
final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
)

object UpdateTableSeatStateRequest:
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW
