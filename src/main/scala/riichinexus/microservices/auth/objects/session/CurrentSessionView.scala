package riichinexus.microservices.auth.objects.session

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.session.SessionPrincipalKind
import upickle.default.{ReadWriter, macroRW}

/** 前端进入应用时用于判定身份、导航和权限入口的当前会话视图。
  *
  * 视图同时表达主体类别、是否已认证、角色标记，以及可选的玩家或游客详情，避免页面再分别调用多个身份接口。
  */
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
