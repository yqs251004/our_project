package riichinexus.microservices.auth.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AuthSuccessView(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
) derives CanEqual

object AuthSuccessView:
  given ReadWriter[AuthSuccessView] = macroRW
