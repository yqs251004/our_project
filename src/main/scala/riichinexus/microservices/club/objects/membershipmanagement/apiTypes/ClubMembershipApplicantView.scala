package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.*

final case class ClubMembershipApplicantView(
    playerId: Option[String],
    applicantUserId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[String]
) derives ReadWriter
