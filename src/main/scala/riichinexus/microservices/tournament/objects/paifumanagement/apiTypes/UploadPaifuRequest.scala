package riichinexus.microservices.tournament.objects.paifumanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu
import upickle.default.*

final case class UploadPaifuRequest(
    operatorId: Option[String] = None,
    paifu: Paifu
)

object UploadPaifuRequest:
  given ReadWriter[UploadPaifuRequest] = macroRW
