package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class ApproveClubApplicationRequest(
    playerId: String,
    operatorId: String,
    note: Option[String] = None
)

object ApproveClubApplicationRequest:
  given ReadWriter[ApproveClubApplicationRequest] = macroRW
