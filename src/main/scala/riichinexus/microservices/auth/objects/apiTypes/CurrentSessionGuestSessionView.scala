package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
)

object CurrentSessionGuestSessionView:
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW
