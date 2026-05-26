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
):
  def toRankSnapshot: RankSnapshot =
    RankSnapshot(RankPlatform.valueOf(rankPlatform), tier, stars)

object CreatePlayerRequest:
  given ReadWriter[CreatePlayerRequest] = macroRW
