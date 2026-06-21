package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView

import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.PublicClubLineupMemberView
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.PublicClubRecentMatchView
import upickle.default.{ReadWriter, macroRW}

/** 俱乐部公开详情页的一次性展示模型。
  *
  * 它整合公开统计、关系、荣誉、入会策略、当前赛事阵容和近期对局，让访客无需管理权限也能理解俱乐部状态。
  */
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
