package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** SettleTournamentRequest 表示Settle赛事请求 的前端请求参数。 */

final case class SettleTournamentRequest(
    operatorId: String,
    finalStageId: String,
    prizePool: Long = 0L,
    payoutRatios: Vector[Double] = Vector.empty,
    houseFeeAmount: Long = 0L,
    clubShareRatio: Double = 0.0,
    adjustments: Vector[SettlementAdjustmentRequest] = Vector.empty,
    finalizeSettlement: Boolean = true,
    note: Option[String] = None
)

object SettleTournamentRequest:
  given ReadWriter[SettleTournamentRequest] = macroRW
