package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 生成结算时由前端提交的单个玩家奖惩调整。
  *
  * 它使用字符串玩家 ID 作为 API 输入，后端会转换为领域 ID 并并入结算快照的调整明细。
  */
final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
)

object SettlementAdjustmentRequest:
  given ReadWriter[SettlementAdjustmentRequest] = macroRW
