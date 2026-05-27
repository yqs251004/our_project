package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ForceResetTableRequest(
    operatorId: String,
    note: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ForceResetTableRequest:
  given ReadWriter[ForceResetTableRequest] = macroRW

