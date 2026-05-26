package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ReopenAppealRequest:
  given ReadWriter[ReopenAppealRequest] = macroRW

