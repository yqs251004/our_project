package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** LogoutResponse 表示登出响应 的 API 响应结果。 */

final case class LogoutResponse(
    message: String
)

object LogoutResponse:
  given ReadWriter[LogoutResponse] = macroRW
