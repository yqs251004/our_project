package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.*

final case class PublicClubRecentMatchView(
    matchRecordId: String,
    tournamentId: String,
    tournamentName: String,
    stageId: String,
    stageName: String,
    tableId: String,
    generatedAt: String,
    seats: Vector[PublicClubRecentMatchSeatView]
) derives CanEqual

object PublicClubRecentMatchView:
  given ReadWriter[PublicClubRecentMatchView] = macroRW
