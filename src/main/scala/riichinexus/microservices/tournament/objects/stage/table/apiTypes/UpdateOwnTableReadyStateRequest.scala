package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** UpdateOwnTableReadyStateRequest 表示更新Own牌桌Ready状态请求 的前端请求参数。 */

final case class UpdateOwnTableReadyStateRequest(
    operatorId: String,
    ready: Boolean = true,
    note: Option[String] = None
)

object UpdateOwnTableReadyStateRequest:
  given ReadWriter[UpdateOwnTableReadyStateRequest] = macroRW
