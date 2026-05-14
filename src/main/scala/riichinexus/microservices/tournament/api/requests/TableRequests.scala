package riichinexus.microservices.tournament.api.requests

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

final case class UploadPaifuRequest(
    operatorId: String,
    paifu: Paifu
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class ForceResetTableRequest(
    operatorId: String,
    note: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
):
  require(ready.isDefined || disconnected.isDefined, "Seat state update must modify at least one flag")

  def operator: PlayerId =
    PlayerId(operatorId)

object TableRequests:
  given ReadWriter[UpdateOwnTableReadyStateRequest] = macroRW
  given ReadWriter[UploadPaifuRequest] = macroRW
  given ReadWriter[ForceResetTableRequest] = macroRW
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW
