package riichinexus.microservices.tournament.domain.settlement.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}

import riichinexus.microservices.tournament.objects.settlementmanagement.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
/** TournamentSettlementSnapshot 表示后端领域中的赛事结算快照状态或规则，包含 ID、赛事 ID、阶段 ID、revision、状态、生成时间等。 */
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
)