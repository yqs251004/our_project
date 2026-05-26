package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateOwnTableReadyStateRequest(
    operatorId: String,
    ready: Boolean = true,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object UpdateOwnTableReadyStateRequest:
  given ReadWriter[UpdateOwnTableReadyStateRequest] = macroRW

