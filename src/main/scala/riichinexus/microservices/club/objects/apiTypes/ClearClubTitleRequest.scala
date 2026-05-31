package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class ClearClubTitleRequest(
    operatorId: String,
    note: Option[String] = None
)

object ClearClubTitleRequest:
  given ReadWriter[ClearClubTitleRequest] = macroRW
