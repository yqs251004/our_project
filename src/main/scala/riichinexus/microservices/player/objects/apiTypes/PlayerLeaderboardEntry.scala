package riichinexus.microservices.player.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.*

final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
) derives CanEqual

object PlayerLeaderboardEntry:
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
