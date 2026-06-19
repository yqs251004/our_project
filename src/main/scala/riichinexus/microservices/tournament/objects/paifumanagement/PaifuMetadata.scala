package riichinexus.microservices.tournament.objects.paifumanagement

import java.time.Instant

import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat

/** PaifuMetadata 表示前后端共享的牌谱Metadata 数据结构，包含recordedAt、source、牌桌 ID、赛事 ID、阶段 ID、座位等。 */

final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
)
