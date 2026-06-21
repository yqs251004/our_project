package riichinexus.microservices.club.domain

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.domain.clubmanagement.model.ClubHonor
import riichinexus.microservices.club.domain.membershipmanagement.model.{ClubMemberContribution, ClubMembershipApplication, ClubRecruitmentPolicy, ClubTitleAssignment}
import riichinexus.microservices.club.domain.rankprivilegemanagement.functions.ClubDefaultRankFunctions
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部领域聚合的根状态。
  *
  * 俱乐部聚合保存成员、管理员、资金与点数池、等级树、贡献、称号、荣誉、关系、招募策略和入会申请，是俱乐部管理接口的主要持久化对象。
  */
final case class Club(
    id: ClubId,
    name: String,
    creator: PlayerId,
    createdAt: Instant,
    members: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    totalPoints: Int = 0,
    treasuryBalance: Long = 0L,
    pointPool: Int = 0,
    rankTree: Vector[ClubRankNode] = ClubDefaultRankFunctions.defaultRankTree,
    memberContributions: Vector[ClubMemberContribution] = Vector.empty,
    titleAssignments: Vector[ClubTitleAssignment] = Vector.empty,
    powerRating: Double = 0.0,
    honors: Vector[ClubHonor] = Vector.empty,
    relations: Vector[ClubRelation] = Vector.empty,
    recruitmentPolicy: ClubRecruitmentPolicy = ClubRecruitmentPolicy(),
    membershipApplications: Vector[ClubMembershipApplication] = Vector.empty,
    dissolvedAt: Option[Instant] = None,
    dissolvedBy: Option[PlayerId] = None,
    version: Int = 0
)
