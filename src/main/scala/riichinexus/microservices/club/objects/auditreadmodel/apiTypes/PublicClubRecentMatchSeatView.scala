package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.*
import riichinexus.system.json.JsonCodecs.given

final case class PublicClubRecentMatchSeatView(
    playerId: String,
    nickname: String,
    clubId: Option[String],
    seat: String,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
)

object PublicClubRecentMatchSeatView:
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW
