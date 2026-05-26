package riichinexus.microservices.auth.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionRoleFlags(
    isGuest: Boolean,
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
) derives CanEqual

object CurrentSessionRoleFlags:
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
