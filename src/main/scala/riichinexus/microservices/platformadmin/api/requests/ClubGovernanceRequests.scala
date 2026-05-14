package riichinexus.microservices.platformadmin.api.requests

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class DissolveClubRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

object DissolveClubRequest:
  given ReadWriter[DissolveClubRequest] = macroRW
