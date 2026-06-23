package riichinexus.microservices.auth.objects.session.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 撤销游客会话时附带的可选说明。
  *
  * 原因会写入会话状态，供后台排查用户主动退出、风控处理或设备切换等不同撤销来源。
  */
final case class RevokeGuestSessionRequest(
    reason: Option[String] = None
)

object RevokeGuestSessionRequest:
  given ReadWriter[RevokeGuestSessionRequest] = macroRW
