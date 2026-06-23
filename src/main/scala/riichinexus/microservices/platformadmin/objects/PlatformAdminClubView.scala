package riichinexus.microservices.platformadmin.objects

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 平台管理页查看俱乐部时使用的后台视图。
  *
  * 它比公开目录多出创建者、创建时间和解散信息，便于平台管理员判断俱乐部状态与处理异常。
  */
final case class PlatformAdminClubView(
    clubId: String,
    name: String,
    creator: String,
    createdAt: String,
    memberCount: Int,
    adminCount: Int,
    totalPoints: Int,
    powerRating: Double,
    dissolvedAt: Option[String],
    dissolvedBy: Option[String]
)

object PlatformAdminClubView:
  given ReadWriter[PlatformAdminClubView] = macroRW
