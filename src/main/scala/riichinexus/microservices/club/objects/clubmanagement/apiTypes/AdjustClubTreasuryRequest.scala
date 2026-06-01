package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
)

object AdjustClubTreasuryRequest:
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW
