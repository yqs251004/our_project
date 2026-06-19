package riichinexus.microservices.player.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.{ReadWriter, macroRW}

/** PlayerLeaderboardEntry 表示前后端共享的玩家Leaderboard条目 数据结构。 */

final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
)

object PlayerLeaderboardEntry:
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
