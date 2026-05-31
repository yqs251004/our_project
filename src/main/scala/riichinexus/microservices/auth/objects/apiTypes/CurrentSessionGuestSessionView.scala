package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
) derives CanEqual

object CurrentSessionGuestSessionView:
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW
