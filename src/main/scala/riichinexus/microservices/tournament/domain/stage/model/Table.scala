package riichinexus.microservices.tournament.domain.stage.model


import java.time.Instant

import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.objects.AppealTicketId
import riichinexus.microservices.tournament.objects.stage.table.{TableSeat, TableStatus}

import riichinexus.system.json.JsonCodecs.given

/** 一张赛事牌桌的运行状态。
  *
  * 牌桌连接赛事阶段、轮次、座位、淘汰赛节点、计分时间、牌谱、对局归档和申诉工单，是从准备、对局、计分到结束的状态机载体。
  */
final case class Table(
    id: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    stageRoundNumber: Int = 1,
    bracketMatchId: Option[String] = None,
    bracketRoundNumber: Option[Int] = None,
    feederMatchIds: Vector[String] = Vector.empty,
    status: TableStatus = TableStatus.WaitingPreparation,
    startedAt: Option[Instant] = None,
    scoringStartedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    paifuId: Option[PaifuId] = None,
    matchRecordId: Option[MatchRecordId] = None,
    appealTicketIds: Vector[AppealTicketId] = Vector.empty,
    resetCount: Int = 0,
    operatorNotes: Vector[String] = Vector.empty,
    version: Int = 0
)
