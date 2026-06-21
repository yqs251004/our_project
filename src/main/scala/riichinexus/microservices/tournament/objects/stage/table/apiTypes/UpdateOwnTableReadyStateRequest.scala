package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 玩家更新自己牌桌准备状态的请求体。
  *
  * 与管理员座位状态接口不同，这个请求只作用于当前操作者自己的座位，用于等待开局阶段的就绪切换。
  */
final case class UpdateOwnTableReadyStateRequest(
    operatorId: String,
    ready: Boolean = true,
    note: Option[String] = None
)

object UpdateOwnTableReadyStateRequest:
  given ReadWriter[UpdateOwnTableReadyStateRequest] = macroRW
