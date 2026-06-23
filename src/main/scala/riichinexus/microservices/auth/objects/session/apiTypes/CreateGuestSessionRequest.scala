package riichinexus.microservices.auth.objects.session.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 为未登录访客创建临时会话的请求体。
  *
  * 调用方可以传入昵称、有效时长和设备指纹；省略时由认证服务使用默认游客名和默认 TTL。
  */
final case class CreateGuestSessionRequest(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
)

object CreateGuestSessionRequest:
  given ReadWriter[CreateGuestSessionRequest] = macroRW
