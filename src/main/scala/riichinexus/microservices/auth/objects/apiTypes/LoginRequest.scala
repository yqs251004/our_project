package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** LoginRequest 表示登录请求 的前端请求参数。 */

final case class LoginRequest(
    username: String,
    password: String
)

object LoginRequest:
  given ReadWriter[LoginRequest] = macroRW
