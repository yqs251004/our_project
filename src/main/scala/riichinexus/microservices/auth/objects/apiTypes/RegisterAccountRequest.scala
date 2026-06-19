package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** RegisterAccountRequest 表示注册账号请求 的前端请求参数。 */

final case class RegisterAccountRequest(
    username: String,
    password: String,
    displayName: String
)

object RegisterAccountRequest:
  given ReadWriter[RegisterAccountRequest] = macroRW
