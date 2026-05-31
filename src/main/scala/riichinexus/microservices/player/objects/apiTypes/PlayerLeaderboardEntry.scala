package riichinexus.microservices.player.objects.apiTypes

import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView
import upickle.default.*

final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshotView,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
) derives CanEqual

object PlayerLeaderboardEntry:
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
