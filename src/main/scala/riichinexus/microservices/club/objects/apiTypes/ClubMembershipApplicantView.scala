package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import upickle.default.*

final case class ClubMembershipApplicantView(
    playerId: Option[String],
    applicantUserId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshotView],
    elo: Option[Int],
    clubIds: Vector[String]
) derives ReadWriter
