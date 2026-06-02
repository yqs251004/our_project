package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
)

object CurrentSessionPlayerView:
  given ReadWriter[CurrentSessionPlayerView] = macroRW
