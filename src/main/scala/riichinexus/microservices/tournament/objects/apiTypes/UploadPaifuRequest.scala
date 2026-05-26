package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UploadPaifuRequest(
    operatorId: Option[String] = None,
    paifu: Paifu
):
  def operator: Option[PlayerId] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))

object UploadPaifuRequest:
  given ReadWriter[UploadPaifuRequest] = macroRW

