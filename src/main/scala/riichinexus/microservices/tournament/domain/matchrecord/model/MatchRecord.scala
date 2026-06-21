package riichinexus.microservices.tournament.domain.matchrecord.model


import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

import riichinexus.system.json.JsonCodecs.given

/** 牌桌结束后生成的对局归档记录。
  *
  * 记录把赛事阶段、牌桌、轮次、座位成绩、可选牌谱和最终确认人关联起来，作为排行榜、结算和审计复盘的共同事实来源。
  */
final case class MatchRecord(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResult],
    paifuId: Option[PaifuId] = None,
    finalizedBy: Option[PlayerId] = None,
    sourceEvent: String = "table-state-machine",
    notes: Vector[String] = Vector.empty
)
