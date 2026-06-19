package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** UpgradeGuestSessionRequest 表示Upgrade游客会话请求 的前端请求参数。 */

final case class UpgradeGuestSessionRequest(
    playerId: String
)

object UpgradeGuestSessionRequest:
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW
