package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** RevokeGuestSessionRequest 表示撤销游客会话请求 的前端请求参数。 */

final case class RevokeGuestSessionRequest(
    reason: Option[String] = None
)

object RevokeGuestSessionRequest:
  given ReadWriter[RevokeGuestSessionRequest] = macroRW
