package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AuthSessionView(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
) derives CanEqual

object AuthSessionView:
  given ReadWriter[AuthSessionView] = macroRW
