package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
) derives CanEqual

object CurrentSessionPlayerView:
  given ReadWriter[CurrentSessionPlayerView] = macroRW
