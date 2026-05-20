package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentSettlementAdjustmentView(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String]
) derives CanEqual

object TournamentSettlementAdjustmentView:
  def fromDomain(adjustment: TournamentSettlementAdjustment): TournamentSettlementAdjustmentView =
    TournamentSettlementAdjustmentView(adjustment.playerId, adjustment.label, adjustment.amount, adjustment.note)

final case class TournamentSettlementEntryView(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long,
    deductionAmount: Long,
    clubId: Option[ClubId],
    clubShareAmount: Long,
    playerRetainedAmount: Long,
    finalPoints: Int,
    champion: Boolean
) derives CanEqual

object TournamentSettlementEntryView:
  def fromDomain(entry: TournamentSettlementEntry): TournamentSettlementEntryView =
    TournamentSettlementEntryView(
      playerId = entry.playerId,
      rank = entry.rank,
      awardAmount = entry.awardAmount,
      baseAwardAmount = entry.baseAwardAmount,
      adjustmentAmount = entry.adjustmentAmount,
      deductionAmount = entry.deductionAmount,
      clubId = entry.clubId,
      clubShareAmount = entry.clubShareAmount,
      playerRetainedAmount = entry.playerRetainedAmount,
      finalPoints = entry.finalPoints,
      champion = entry.champion
    )

final case class TournamentSettlementView(
    settlementId: SettlementSnapshotId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    revision: Int,
    status: TournamentSettlementStatus,
    generatedAt: Instant,
    finalizedAt: Option[Instant],
    supersededAt: Option[Instant],
    supersedesSettlementId: Option[SettlementSnapshotId],
    championId: PlayerId,
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
      settlementId = snapshot.id,
      tournamentId = snapshot.tournamentId,
      stageId = snapshot.stageId,
      revision = snapshot.revision,
      status = snapshot.status,
      generatedAt = snapshot.generatedAt,
      finalizedAt = snapshot.finalizedAt,
      supersededAt = snapshot.supersededAt,
      supersedesSettlementId = snapshot.supersedesSettlementId,
      championId = snapshot.championId,
      prizePool = snapshot.prizePool,
      houseFeeAmount = snapshot.houseFeeAmount,
      netPrizePool = snapshot.netPrizePool,
      clubShareRatio = snapshot.clubShareRatio,
      adjustments = snapshot.adjustments.map(TournamentSettlementAdjustmentView.fromDomain),
      entries = snapshot.entries.map(TournamentSettlementEntryView.fromDomain),
      summary = snapshot.summary
    )
