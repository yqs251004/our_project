package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

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
