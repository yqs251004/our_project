package riichinexus.microservices.publicquery.objects.apiTypes

import riichinexus.domain.model.ClubId
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

  def apply(
      clubId: ClubId,
      name: String,
      powerRating: Double,
      totalPoints: Int,
      memberCount: Int
  ): ClubLeaderboardEntry =
    ClubLeaderboardEntry(clubId.value, name, powerRating, totalPoints, memberCount)
