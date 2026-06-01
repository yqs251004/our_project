package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.*

final case class ClubLeaderboardEntry(
    clubId: String,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
) derives CanEqual

object ClubLeaderboardEntry:
  given ReadWriter[ClubLeaderboardEntry] = macroRW
