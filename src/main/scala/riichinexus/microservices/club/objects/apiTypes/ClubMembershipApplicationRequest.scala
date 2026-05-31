package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{GuestSessionId, PlayerId}
import upickle.default.*

final case class ClubMembershipApplicationRequest(
    applicantUserId: Option[String] = None,
    displayName: String,
    message: Option[String] = None,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None
)

object ClubMembershipApplicationRequest:
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW
