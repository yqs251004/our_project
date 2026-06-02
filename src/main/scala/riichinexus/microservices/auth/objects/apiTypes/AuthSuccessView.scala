package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AuthSuccessView(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
)

object AuthSuccessView:
  given ReadWriter[AuthSuccessView] = macroRW
