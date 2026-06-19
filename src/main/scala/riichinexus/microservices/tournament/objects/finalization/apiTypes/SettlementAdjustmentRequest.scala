package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** SettlementAdjustmentRequest 表示结算调整请求 的前端请求参数。 */

final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
)

object SettlementAdjustmentRequest:
  given ReadWriter[SettlementAdjustmentRequest] = macroRW
