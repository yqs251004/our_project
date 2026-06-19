package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** CreateGuestSessionRequest 表示创建游客会话请求 的前端请求参数。 */

final case class CreateGuestSessionRequest(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
)

object CreateGuestSessionRequest:
  given ReadWriter[CreateGuestSessionRequest] = macroRW
