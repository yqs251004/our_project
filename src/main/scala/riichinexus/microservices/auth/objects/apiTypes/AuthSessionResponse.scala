package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
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
