package riichinexus.microservices.player.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 玩家资料中面向前端展示的角色标记。
  *
  * 这些布尔值用于快速渲染管理入口和身份徽标；具体 API 权限仍由后端按角色授予和资源作用域判断。
  */
final case class PlayerRoleFlagsView(
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
)

object PlayerRoleFlagsView:
  given ReadWriter[PlayerRoleFlagsView] = macroRW
