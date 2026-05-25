package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentSettlementAdjustmentView(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String]
) derives CanEqual

object TournamentSettlementAdjustmentView:
  def fromDomain(adjustment: TournamentSettlementAdjustment): TournamentSettlementAdjustmentView =
    TournamentSettlementAdjustmentView(adjustment.playerId.value, adjustment.label, adjustment.amount, adjustment.note)

final case class TournamentSettlementEntryView(
    playerId: String,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long,
    deductionAmount: Long,
    clubId: Option[String],
    clubShareAmount: Long,
    playerRetainedAmount: Long,
    finalPoints: Int,
    champion: Boolean
) derives CanEqual

object TournamentSettlementEntryView:
  def fromDomain(entry: TournamentSettlementEntry): TournamentSettlementEntryView =
    TournamentSettlementEntryView(
      playerId = entry.playerId.value,
      rank = entry.rank,
      awardAmount = entry.awardAmount,
      baseAwardAmount = entry.baseAwardAmount,
      adjustmentAmount = entry.adjustmentAmount,
      deductionAmount = entry.deductionAmount,
      clubId = entry.clubId.map(_.value),
      clubShareAmount = entry.clubShareAmount,
      playerRetainedAmount = entry.playerRetainedAmount,
      finalPoints = entry.finalPoints,
      champion = entry.champion
    )

final case class TournamentSettlementView(
    settlementId: String,
    tournamentId: String,
    stageId: String,
    revision: Int,
    status: String,
    generatedAt: String,
    finalizedAt: Option[String],
    supersededAt: Option[String],
    supersedesSettlementId: Option[String],
    championId: String,
    prizePool: Long,
    houseFeeAmount: Long,
    netPrizePool: Long,
    clubShareRatio: Double,
    adjustments: Vector[TournamentSettlementAdjustmentView],
    entries: Vector[TournamentSettlementEntryView],
      summary: String
) derives CanEqual

object TournamentSettlementView:
  def fromDomain(snapshot: TournamentSettlementSnapshot): TournamentSettlementView =
    TournamentSettlementView(
      settlementId = snapshot.id.value,
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      revision = snapshot.revision,
      status = snapshot.status.toString,
      generatedAt = snapshot.generatedAt.toString,
      finalizedAt = snapshot.finalizedAt.map(_.toString),
      supersededAt = snapshot.supersededAt.map(_.toString),
      supersedesSettlementId = snapshot.supersedesSettlementId.map(_.value),
      championId = snapshot.championId.value,
      prizePool = snapshot.prizePool,
      houseFeeAmount = snapshot.houseFeeAmount,
      netPrizePool = snapshot.netPrizePool,
      clubShareRatio = snapshot.clubShareRatio,
      adjustments = snapshot.adjustments.map(TournamentSettlementAdjustmentView.fromDomain),
      entries = snapshot.entries.map(TournamentSettlementEntryView.fromDomain),
      summary = snapshot.summary
    )
