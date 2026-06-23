package riichinexus.microservices.auth.objects.session

import upickle.default.{ReadWriter, macroRW}

/** 登录或注册成功后返回给前端的认证结果。
  *
  * 与普通会话视图不同，这里包含新签发的访问 token，前端会据此保存后续 API 调用所需的认证信息。
  */
final case class AuthSuccessView(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
)

object AuthSuccessView:
  given ReadWriter[AuthSuccessView] = macroRW
