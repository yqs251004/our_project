package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.{AuthSessionView, CurrentSessionRoleFlags}
import upickle.default.*

final case class AuthSessionResponse(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
)

object AuthSessionResponse:
  given ReadWriter[AuthSessionResponse] = macroRW

  def fromView(view: AuthSessionView): AuthSessionResponse =
    AuthSessionResponse(
      userId = view.userId,
      username = view.username,
      displayName = view.displayName,
      authenticated = view.authenticated,
      roles = view.roles
    )
