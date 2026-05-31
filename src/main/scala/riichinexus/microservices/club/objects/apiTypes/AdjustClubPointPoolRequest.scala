package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AdjustClubPointPoolRequest(
    operatorId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubPointPoolRequest:
  given ReadWriter[AdjustClubPointPoolRequest] = macroRW
