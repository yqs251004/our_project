package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import upickle.default.*

final case class CurrentSessionView(
    principalKind: SessionPrincipalKind,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[CurrentSessionPlayerView] = None,
    guestSession: Option[CurrentSessionGuestSessionView] = None
) derives CanEqual

object CurrentSessionView:
  given ReadWriter[CurrentSessionView] = macroRW
