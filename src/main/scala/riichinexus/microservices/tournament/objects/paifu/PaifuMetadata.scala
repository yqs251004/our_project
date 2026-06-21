package riichinexus.microservices.tournament.objects.paifu

import java.time.Instant

import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.stage.table.TableSeat

/** 牌谱归档时附带的来源和归属信息。
  *
  * 元数据把牌谱绑定到牌桌、赛事、阶段、座位分配和可选对局记录，`source` 说明它来自上传、实时归档或其他导入渠道。
  */
final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
)
