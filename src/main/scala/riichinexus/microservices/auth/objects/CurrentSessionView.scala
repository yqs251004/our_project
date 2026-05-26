package riichinexus.microservices.auth.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionView(
    principalKind: String,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[CurrentSessionPlayerView] = None,
    guestSession: Option[CurrentSessionGuestSessionView] = None
) derives CanEqual

object CurrentSessionView:
  given ReadWriter[CurrentSessionView] = macroRW
