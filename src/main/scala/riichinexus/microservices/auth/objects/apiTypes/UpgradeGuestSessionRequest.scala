package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpgradeGuestSessionRequest(
    playerId: String
):
  def player: PlayerId =
    PlayerId(playerId)

object UpgradeGuestSessionRequest:
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW
