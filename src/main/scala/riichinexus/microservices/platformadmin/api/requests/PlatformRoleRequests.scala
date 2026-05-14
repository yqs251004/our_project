package riichinexus.microservices.platformadmin.api.requests

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class GrantSuperAdminRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

object GrantSuperAdminRequest:
  given ReadWriter[GrantSuperAdminRequest] = macroRW
