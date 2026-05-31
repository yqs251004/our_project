package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpgradeGuestSessionRequest(
    playerId: String
)

object UpgradeGuestSessionRequest:
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW
