package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AuthSessionView(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
)

object AuthSessionView:
  given ReadWriter[AuthSessionView] = macroRW
