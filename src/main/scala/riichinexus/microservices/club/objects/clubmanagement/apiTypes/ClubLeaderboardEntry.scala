package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** ClubLeaderboardEntry 表示前后端共享的俱乐部Leaderboard条目 数据结构。 */

final case class ClubLeaderboardEntry(
    clubId: String,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
)

object ClubLeaderboardEntry:
  given ReadWriter[ClubLeaderboardEntry] = macroRW
