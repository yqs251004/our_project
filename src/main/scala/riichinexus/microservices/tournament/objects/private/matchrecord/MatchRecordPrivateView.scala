package riichinexus.microservices.tournament.objects.`private`.matchrecord

import java.time.Instant

import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

/** MatchRecordPrivateView 表示后端内部使用的对局记录后端内部视图 read model，包含 ID、牌桌 ID、赛事 ID、阶段 ID、生成时间、seatResults。 */

final case class MatchRecordPrivateView(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResultPrivateView]
)
