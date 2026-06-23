package riichinexus.microservices.auth.objects.session

import upickle.default.{ReadWriter, macroRW}

/** 当前会话中前端最常用的角色布尔快照。
  *
  * 这些标记用于显示或隐藏页面入口；真正的写操作权限仍由后端根据角色授予记录和资源作用域校验。
  */
final case class CurrentSessionRoleFlags(
    isGuest: Boolean,
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
)

object CurrentSessionRoleFlags:
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
