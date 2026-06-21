package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 俱乐部排行榜中的排名候选摘要。
  *
  * 排行展示依赖战力、总点数和成员数这些公开指标，不包含成员列表或管理资产明细。
  */
final case class ClubLeaderboardEntry(
    clubId: String,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
)

object ClubLeaderboardEntry:
  given ReadWriter[ClubLeaderboardEntry] = macroRW
