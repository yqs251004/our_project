package riichinexus.microservices.player.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PlayerRoleFlagsView(
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
) derives CanEqual

object PlayerRoleFlagsView:
  given ReadWriter[PlayerRoleFlagsView] = macroRW
