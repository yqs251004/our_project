package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ResolveAppealRequest(
    operatorId: String,
    verdict: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ResolveAppealRequest:
  given ReadWriter[ResolveAppealRequest] = macroRW

