package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class RejectClubApplicationRequest(
    operatorId: String,
    note: Option[String] = None
)

object RejectClubApplicationRequest:
  given ReadWriter[RejectClubApplicationRequest] = macroRW
