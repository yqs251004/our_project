package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** GuestSessionResponse 表示游客会话响应 的 API 响应结果。 */

final case class GuestSessionResponse(
    id: String,
    displayName: String,
    createdAt: String
)

object GuestSessionResponse:
  given ReadWriter[GuestSessionResponse] = macroRW
