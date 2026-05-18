package riichinexus.microservices.platformadmin.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object PlatformAdminResponseCodecs:
  given ReadWriter[PlatformAdminPlayerView] = macroRW
  given ReadWriter[PlatformAdminClubView] = macroRW
