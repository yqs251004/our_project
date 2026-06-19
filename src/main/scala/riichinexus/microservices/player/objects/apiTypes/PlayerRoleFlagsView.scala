package riichinexus.microservices.player.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** PlayerRoleFlagsView 表示玩家角色Flags视图 的前端展示视图。 */

final case class PlayerRoleFlagsView(
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
)

object PlayerRoleFlagsView:
  given ReadWriter[PlayerRoleFlagsView] = macroRW
