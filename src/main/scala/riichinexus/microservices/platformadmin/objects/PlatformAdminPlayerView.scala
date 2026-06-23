package riichinexus.microservices.platformadmin.objects

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 平台管理页查看玩家时使用的后台视图。
  *
  * 该视图聚合玩家身份、状态、所属俱乐部、封禁原因和超级管理员标记，服务于封禁与授权操作。
  */
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
