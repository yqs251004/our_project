package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.{
  CurrentSessionGuestSessionView,
  CurrentSessionPlayerView,
  CurrentSessionRoleFlags,
  CurrentSessionView
}
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

  def fromView(view: CurrentSessionView): CurrentSessionResponse =
    CurrentSessionResponse(
      principalKind = view.principalKind,
      principalId = view.principalId,
      displayName = view.displayName,
      authenticated = view.authenticated,
      roles = view.roles,
      player = view.player,
      guestSession = view.guestSession
    )
