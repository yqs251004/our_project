package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class UpdateClubRankTreeRequest(
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
)

object UpdateClubRankTreeRequest:
  given ReadWriter[UpdateClubRankTreeRequest] = macroRW
