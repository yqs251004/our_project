package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class RemoveClubMemberRequest(
    operatorId: Option[String] = None
)

object RemoveClubMemberRequest:
  given ReadWriter[RemoveClubMemberRequest] = macroRW
