package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** TournamentSettlementView 表示赛事结算视图 的前端展示视图。 */

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
