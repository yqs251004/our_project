package riichinexus.microservices.tournament.objects.`private`

import java.time.Instant

import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}

/** MatchRecordPrivateView 表示后端内部使用的对局记录后端内部视图 read model，包含 ID、牌桌 ID、赛事 ID、阶段 ID、生成时间、seatResults。 */

final case class MatchRecordPrivateView(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResultPrivateView]
)
