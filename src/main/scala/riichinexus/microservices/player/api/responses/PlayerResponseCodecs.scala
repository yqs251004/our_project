package riichinexus.microservices.player.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object PlayerResponseCodecs:
  given ReadWriter[PlayerRoleFlagsView] = macroRW
  given ReadWriter[PlayerProfileView] = macroRW
