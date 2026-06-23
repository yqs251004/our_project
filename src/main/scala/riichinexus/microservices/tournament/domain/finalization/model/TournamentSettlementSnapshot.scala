package riichinexus.microservices.tournament.domain.finalization.model

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given

/** 某一赛事阶段结算结果的不可变快照。
  *
  * 快照记录奖金池、平台费用、俱乐部分成、人工调整、选手结算明细和版本关系，便于结算被确认、替换或追溯时保留完整历史。
  */
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
