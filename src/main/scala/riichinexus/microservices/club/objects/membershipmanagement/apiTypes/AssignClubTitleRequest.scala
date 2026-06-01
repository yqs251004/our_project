package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object AssignClubTitleRequest:
  given ReadWriter[AssignClubTitleRequest] = macroRW
