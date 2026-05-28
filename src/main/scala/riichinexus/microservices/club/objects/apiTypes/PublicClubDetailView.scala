package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.ClubId
import upickle.default.*

final case class PublicClubDetailView(
    clubId: String,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    relations: Vector[PublicClubRelationView],
    honors: Vector[PublicClubHonorView],
    applicationPolicy: ClubApplicationPolicyView,
    currentLineup: Vector[PublicClubLineupMemberView],
    recentMatches: Vector[PublicClubRecentMatchView]
) derives CanEqual

object PublicClubDetailView:
  given ReadWriter[PublicClubDetailView] = macroRW

  def apply(
      clubId: ClubId,
      name: String,
      memberCount: Int,
      activeMemberCount: Int,
      adminCount: Int,
      powerRating: Double,
      totalPoints: Int,
      treasuryBalance: Long,
      pointPool: Int,
      relations: Vector[PublicClubRelationView],
      honors: Vector[PublicClubHonorView],
      applicationPolicy: ClubApplicationPolicyView,
      currentLineup: Vector[PublicClubLineupMemberView],
      recentMatches: Vector[PublicClubRecentMatchView]
  ): PublicClubDetailView =
    PublicClubDetailView(
      clubId = clubId.value,
      name = name,
      memberCount = memberCount,
      activeMemberCount = activeMemberCount,
      adminCount = adminCount,
      powerRating = powerRating,
      totalPoints = totalPoints,
      treasuryBalance = treasuryBalance,
      pointPool = pointPool,
      relations = relations,
      honors = honors,
      applicationPolicy = applicationPolicy,
      currentLineup = currentLineup,
      recentMatches = recentMatches
    )
