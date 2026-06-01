package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: ClubApplicationReviewDecision,
    playerId: Option[String] = None,
    note: Option[String] = None
)

object ReviewClubApplicationRequest:
  given ReadWriter[ReviewClubApplicationRequest] = macroRW
