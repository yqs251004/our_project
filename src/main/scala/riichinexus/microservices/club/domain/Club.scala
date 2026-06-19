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
/** Club 表示后端领域中的俱乐部状态或规则，包含 ID、名称、creator、创建时间、成员、管理员等。 */
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