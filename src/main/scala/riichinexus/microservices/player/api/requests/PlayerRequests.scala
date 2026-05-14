package riichinexus.microservices.player.api.requests

import riichinexus.domain.model.*
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

object PlayerRequests:
  given ReadWriter[CreatePlayerRequest] = macroRW
