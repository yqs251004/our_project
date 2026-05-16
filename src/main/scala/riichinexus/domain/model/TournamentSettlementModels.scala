package riichinexus.domain.model

import java.time.Instant

final case class TournamentSettlementEntry(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long = 0L,
    deductionAmount: Long = 0L,
    clubId: Option[ClubId] = None,
    clubShareAmount: Long = 0L,
    playerRetainedAmount: Long = 0L,
    finalPoints: Int,
    champion: Boolean = false
) derives CanEqual

enum TournamentSettlementStatus derives CanEqual:
  case Draft
  case Finalized
  case Superseded

final case class TournamentSettlementAdjustment(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String] = None
) derives CanEqual:
  require(label.trim.nonEmpty, "Tournament settlement adjustment label cannot be empty")

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
