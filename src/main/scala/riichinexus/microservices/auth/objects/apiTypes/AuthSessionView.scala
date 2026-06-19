package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** AuthSessionView 表示认证会话视图 的前端展示视图。 */

final case class AuthSessionView(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
)

object AuthSessionView:
  given ReadWriter[AuthSessionView] = macroRW
