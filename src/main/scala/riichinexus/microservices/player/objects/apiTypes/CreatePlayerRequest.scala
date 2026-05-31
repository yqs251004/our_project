package riichinexus.microservices.player.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import upickle.default.*

final case class CreatePlayerRequest(
    userId: String,
    nickname: String,
    rankPlatform: String,
    tier: String,
    stars: Option[Int] = None,
    initialElo: Int = 1500
)

object CreatePlayerRequest:
  given ReadWriter[CreatePlayerRequest] = macroRW
