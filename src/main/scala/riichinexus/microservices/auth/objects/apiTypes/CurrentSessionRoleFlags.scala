package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** CurrentSessionRoleFlags 表示前后端共享的当前会话角色Flags 数据结构。 */

final case class CurrentSessionRoleFlags(
    isGuest: Boolean,
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
)

object CurrentSessionRoleFlags:
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
