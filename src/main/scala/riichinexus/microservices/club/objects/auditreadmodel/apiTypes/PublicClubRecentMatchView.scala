package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** PublicClubRecentMatchView 表示公开俱乐部详情页展示的近期对局摘要，包含赛事、阶段、牌桌和各座位成绩。 */

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
