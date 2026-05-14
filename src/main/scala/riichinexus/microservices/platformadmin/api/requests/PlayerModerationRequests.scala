package riichinexus.microservices.platformadmin.api.requests

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class BanPlayerRequest(
    operatorId: String,
    reason: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

object BanPlayerRequest:
  given ReadWriter[BanPlayerRequest] = macroRW
