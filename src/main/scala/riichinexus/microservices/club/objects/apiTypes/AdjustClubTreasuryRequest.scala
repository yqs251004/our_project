package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object AdjustClubTreasuryRequest:
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW
