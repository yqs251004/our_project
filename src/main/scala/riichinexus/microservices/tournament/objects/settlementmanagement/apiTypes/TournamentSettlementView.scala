package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.microservices.tournament.objects.settlementmanagement.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.TournamentSettlementSnapshot
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
) derives CanEqual

object TournamentSettlementView:
  def fromDomain(snapshot: TournamentSettlementSnapshot): TournamentSettlementView =
    TournamentSettlementView(
      settlementId = snapshot.id.value,
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      revision = snapshot.revision,
      status = snapshot.status,
      generatedAt = snapshot.generatedAt.toString,
      finalizedAt = snapshot.finalizedAt.map(_.toString),
      supersededAt = snapshot.supersededAt.map(_.toString),
      supersedesSettlementId = snapshot.supersedesSettlementId.map(_.value),
      championId = snapshot.championId.value,
      prizePool = snapshot.prizePool,
      houseFeeAmount = snapshot.houseFeeAmount,
      netPrizePool = snapshot.netPrizePool,
      clubShareRatio = snapshot.clubShareRatio,
      adjustments = snapshot.adjustments,
      entries = snapshot.entries,
      summary = snapshot.summary
    )

  given ReadWriter[TournamentSettlementView] = macroRW
