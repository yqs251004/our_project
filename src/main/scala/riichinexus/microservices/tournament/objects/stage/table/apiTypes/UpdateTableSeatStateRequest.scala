package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** UpdateTableSeatStateRequest 表示更新牌桌座位状态请求 的前端请求参数。 */

final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
)

object UpdateTableSeatStateRequest:
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW
