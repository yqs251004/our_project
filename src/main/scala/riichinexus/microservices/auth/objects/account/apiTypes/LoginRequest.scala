package riichinexus.microservices.auth.objects.account.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 用户名密码登录接口的请求体。
  *
  * `password` 只用于本次认证校验，服务端会将其与账号凭证中的盐值和哈希比较，不会把明文写入领域模型。
  */
final case class LoginRequest(
    username: String,
    password: String
)

object LoginRequest:
  given ReadWriter[LoginRequest] = macroRW
