package riichinexus.microservices.tournament.objects.matchrecord

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端查看赛事对局结果时使用的完整记录视图。
  *
  * 它连接牌桌、赛事、阶段、座位成绩、可选牌谱、归档人和来源事件，是记录页、结算和申诉入口的共同数据源。
  */
final case class TournamentMatchRecordView(
    recordId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    stageRoundNumber: Int,
    generatedAt: String,
    seatResults: Vector[TournamentMatchRecordSeatResultView],
    paifuId: Option[String],
    finalizedBy: Option[String],
    sourceEvent: String,
    notes: Vector[String]
)

object TournamentMatchRecordView:
  given ReadWriter[TournamentMatchRecordView] = macroRW
