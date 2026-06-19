package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView

import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.PublicClubLineupMemberView
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.PublicClubRecentMatchView
import upickle.default.{ReadWriter, macroRW}

/** PublicClubDetailView 表示公开俱乐部详情视图 的前端展示视图。 */

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
    relations: Vector[ClubRelationView],
    honors: Vector[PublicClubHonorView],
    applicationPolicy: ClubApplicationPolicyView,
    currentLineup: Vector[PublicClubLineupMemberView],
    recentMatches: Vector[PublicClubRecentMatchView]
)

object PublicClubDetailView:
  given ReadWriter[PublicClubDetailView] = macroRW
