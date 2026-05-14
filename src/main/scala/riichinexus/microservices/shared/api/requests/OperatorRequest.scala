package riichinexus.microservices.shared.api.requests

import riichinexus.domain.model.*
import upickle.default.*

final case class OperatorRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object OperatorRequest:
  given ReadWriter[OperatorRequest] = macroRW
