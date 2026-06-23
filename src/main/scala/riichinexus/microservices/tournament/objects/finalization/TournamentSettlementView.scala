package riichinexus.microservices.tournament.objects.finalization

import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端展示和确认赛事结算时使用的完整结算快照。
  *
  * 视图包含版本、状态、生成/确认/替代时间、奖池拆分、人工调整、每位玩家的结算条目和摘要说明。
  */
final case class TournamentSettlementView(
    settlementId: String,
    tournamentId: String,
    stageId: String,
    revision: Int,
    status: TournamentSettlementStatus,
    generatedAt: String,
    finalizedAt: Option[String],
    supersededAt: Option[String],
    supersedesSettlementId: Option[String],
    championId: String,
    prizePool: Long,
    houseFeeAmount: Long,
    netPrizePool: Long,
    clubShareRatio: Double,
    adjustments: Vector[TournamentSettlementAdjustment],
    entries: Vector[TournamentSettlementEntry],
    summary: String
)

object TournamentSettlementView:
  given ReadWriter[TournamentSettlementView] = macroRW
