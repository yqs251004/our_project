package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
)

object AddClubMemberRequest:
  given ReadWriter[AddClubMemberRequest] = macroRW
