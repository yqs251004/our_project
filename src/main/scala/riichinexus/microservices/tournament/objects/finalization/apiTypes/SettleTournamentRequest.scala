package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 根据最终阶段成绩生成赛事结算快照的请求体。
  *
  * 请求包含奖金池、分配比例、平台费用、俱乐部分成、人工调整和是否立即确认，后端据此计算每位玩家的最终发放结果。
  */
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
