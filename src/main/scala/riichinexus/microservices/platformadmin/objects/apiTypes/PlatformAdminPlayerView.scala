package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PlatformAdminPlayerView 表示Platform管理员玩家视图 的前端展示视图。 */

final case class PlatformAdminPlayerView(
    playerId: String,
    userId: String,
    nickname: String,
    status: String,
    clubIds: Vector[String],
    bannedReason: Option[String],
    isSuperAdmin: Boolean
)

object PlatformAdminPlayerView:
  given ReadWriter[PlatformAdminPlayerView] = macroRW
