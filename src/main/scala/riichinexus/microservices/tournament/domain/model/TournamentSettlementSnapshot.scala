package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.{PlayerId, SettlementSnapshotId, TournamentId, TournamentStageId}

final case class TournamentSettlementSnapshot(
    id: SettlementSnapshotId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    revision: Int,
    status: TournamentSettlementStatus,
    generatedAt: Instant,
    finalizedAt: Option[Instant] = None,
    supersededAt: Option[Instant] = None,
    supersedesSettlementId: Option[SettlementSnapshotId] = None,
    championId: PlayerId,
    prizePool: Long,
    houseFeeAmount: Long = 0L,
    netPrizePool: Long,
    clubShareRatio: Double = 0.0,
    adjustments: Vector[TournamentSettlementAdjustment] = Vector.empty,
    entries: Vector[TournamentSettlementEntry],
    summary: String,
    version: Int = 0
) derives CanEqual:
  require(revision > 0, "Tournament settlement revision must be positive")
  require(prizePool >= 0L, "Tournament settlement prize pool must be non-negative")
  require(houseFeeAmount >= 0L, "Tournament settlement houseFeeAmount must be non-negative")
  require(netPrizePool >= 0L, "Tournament settlement netPrizePool must be non-negative")
  require(houseFeeAmount <= prizePool, "Tournament settlement houseFeeAmount cannot exceed prizePool")
  require(clubShareRatio >= 0.0 && clubShareRatio <= 1.0, "Tournament settlement clubShareRatio must be between 0.0 and 1.0")

  def finalize(at: Instant): TournamentSettlementSnapshot =
    require(status == TournamentSettlementStatus.Draft, "Only draft tournament settlements can be finalized")
    copy(
      status = TournamentSettlementStatus.Finalized,
      finalizedAt = Some(at)
    )

  def supersede(at: Instant): TournamentSettlementSnapshot =
    require(status != TournamentSettlementStatus.Superseded, "Tournament settlement is already superseded")
    copy(
      status = TournamentSettlementStatus.Superseded,
      supersededAt = Some(at)
    )
