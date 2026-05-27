package riichinexus.microservices.tournament.objects.apiTypes
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OperatorRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object OperatorRequest:
  given ReadWriter[OperatorRequest] = macroRW
