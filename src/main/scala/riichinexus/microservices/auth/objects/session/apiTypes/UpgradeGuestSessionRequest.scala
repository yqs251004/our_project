package riichinexus.microservices.auth.objects.session.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 将当前游客会话绑定到正式玩家身份的请求体。
  *
  * `playerId` 指向升级后的玩家档案，认证服务会在游客会话中记录这个归属，便于后续恢复身份。
  */
final case class UpgradeGuestSessionRequest(
    playerId: String
)

object UpgradeGuestSessionRequest:
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW
