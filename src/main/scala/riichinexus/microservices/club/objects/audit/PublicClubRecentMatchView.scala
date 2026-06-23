package riichinexus.microservices.club.objects.audit

import upickle.default.{ReadWriter, macroRW}

/** 公开俱乐部详情页展示的一场近期比赛。
  *
  * 该摘要把赛事、阶段、牌桌、生成时间和座位成绩聚合在一起，帮助访客从公开页面了解俱乐部最近的参赛表现。
  */
final case class PublicClubRecentMatchView(
    matchRecordId: String,
    tournamentId: String,
    tournamentName: String,
    stageId: String,
    stageName: String,
    tableId: String,
    generatedAt: String,
    seats: Vector[PublicClubRecentMatchSeatView]
)

object PublicClubRecentMatchView:
  given ReadWriter[PublicClubRecentMatchView] = macroRW
