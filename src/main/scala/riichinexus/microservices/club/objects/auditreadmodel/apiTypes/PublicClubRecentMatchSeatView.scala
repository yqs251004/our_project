package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.*

final case class PublicClubRecentMatchSeatView(
    playerId: String,
    nickname: String,
    clubId: Option[String],
    seat: String,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
) derives CanEqual

object PublicClubRecentMatchSeatView:
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW
