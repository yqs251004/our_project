package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.*

final case class ClubMembershipApplicantView(
    playerId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[String]
) derives ReadWriter
