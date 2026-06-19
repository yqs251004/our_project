package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** AuthSuccessView 表示认证成功结果视图 的前端展示视图。 */

final case class AuthSuccessView(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
)

object AuthSuccessView:
  given ReadWriter[AuthSuccessView] = macroRW
