package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AuthSuccessResponse(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
)

object AuthSuccessResponse:
  given ReadWriter[AuthSuccessResponse] = macroRW
