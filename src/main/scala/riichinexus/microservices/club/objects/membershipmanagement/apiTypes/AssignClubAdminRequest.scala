package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AssignClubAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignClubAdminRequest:
  given ReadWriter[AssignClubAdminRequest] = macroRW
