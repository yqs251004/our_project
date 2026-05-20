package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import upickle.default.*

final case class OperatorRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object OperatorRequest:
  given ReadWriter[OperatorRequest] = macroRW
