package riichinexus.microservices.club.domain

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.clubmanagement.model.ClubHonor
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.functions.ClubDefaultRankFunctions
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode

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
) derives CanEqual
