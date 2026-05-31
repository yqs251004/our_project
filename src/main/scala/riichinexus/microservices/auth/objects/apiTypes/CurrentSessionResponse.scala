package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionResponse(
    principalKind: String,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[CurrentSessionPlayerView] = None,
    guestSession: Option[CurrentSessionGuestSessionView] = None
)

object CurrentSessionResponse:
  given ReadWriter[CurrentSessionResponse] = macroRW
