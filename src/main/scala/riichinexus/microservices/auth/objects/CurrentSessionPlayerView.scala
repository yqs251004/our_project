package riichinexus.microservices.auth.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.Player
import upickle.default.*

final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
) derives CanEqual

object CurrentSessionPlayerView:
  given ReadWriter[CurrentSessionPlayerView] = macroRW

  def fromDomain(player: Player): CurrentSessionPlayerView =
    CurrentSessionPlayerView(
      id = player.id.value,
      userId = player.userId,
      nickname = player.nickname
    )
