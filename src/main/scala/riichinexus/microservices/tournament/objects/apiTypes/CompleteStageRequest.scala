package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CompleteStageRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))

object CompleteStageRequest:
  given ReadWriter[CompleteStageRequest] = macroRW

