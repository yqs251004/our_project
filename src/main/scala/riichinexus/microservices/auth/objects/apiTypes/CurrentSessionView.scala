package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import upickle.default.{ReadWriter, macroRW}

/** CurrentSessionView 表示当前会话视图 的前端展示视图。 */

final case class CurrentSessionView(
    principalKind: SessionPrincipalKind,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[CurrentSessionPlayerView] = None,
    guestSession: Option[CurrentSessionGuestSessionView] = None
)

object CurrentSessionView:
  given ReadWriter[CurrentSessionView] = macroRW
