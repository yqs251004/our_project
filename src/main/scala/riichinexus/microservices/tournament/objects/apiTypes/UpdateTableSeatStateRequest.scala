package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
):
  require(ready.isDefined || disconnected.isDefined, "Seat state update must modify at least one flag")

  def operator: PlayerId =
    PlayerId(operatorId)

object UpdateTableSeatStateRequest:
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW

