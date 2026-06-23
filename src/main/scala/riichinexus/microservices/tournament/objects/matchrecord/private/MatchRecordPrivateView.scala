package riichinexus.microservices.tournament.objects.matchrecord.`private`

import java.time.Instant

import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

/** 服务间读取对局记录时使用的内部视图。
  *
  * 与公开 API 视图不同，它保留强类型 ID 和座位结果内部模型，方便结算、排行榜和俱乐部审计读模型直接计算。
  */
final case class MatchRecordPrivateView(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResultPrivateView]
)
