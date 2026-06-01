package riichinexus.microservices.club.domain.clubmanagement.functions

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.ClubHonor
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.ClubMemberPrivilegeSnapshot
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.objects.rankprivilegemanagement.{ClubPrivilegeCode, ClubRankNode}

object ClubFunctions:
  def addMember(club: Club, playerId: PlayerId): Club =
    if club.members.contains(playerId) then club
    else club.copy(members = club.members :+ playerId)

  def removeMember(club: Club, playerId: PlayerId): Club =
    club.copy(
      members = club.members.filterNot(_ == playerId),
      admins = club.admins.filterNot(_ == playerId),
      memberContributions = club.memberContributions.filterNot(_.playerId == playerId),
      titleAssignments = club.titleAssignments.filterNot(_.playerId == playerId)
    )

  def addPoints(club: Club, points: Int): Club =
    club.copy(
      totalPoints = club.totalPoints + points,
      pointPool = club.pointPool + points
    )

  def adjustTreasury(club: Club, delta: Long): Club =
    require(club.treasuryBalance + delta >= 0L, "Club treasury balance cannot be negative")
    club.copy(treasuryBalance = club.treasuryBalance + delta)

  def adjustPointPool(club: Club, delta: Int): Club =
    require(club.pointPool + delta >= 0, "Club point pool cannot be negative")
    club.copy(pointPool = club.pointPool + delta)

  def addHonor(club: Club, honor: ClubHonor): Club =
    require(honor.title.trim.nonEmpty, "Club honor title cannot be empty")
    val normalizedTitle = honor.title.trim.toLowerCase
    club.copy(
      honors =
        club.honors.filterNot(_.title.trim.toLowerCase == normalizedTitle) :+ honor
    )

  def removeHonor(club: Club, title: String): Club =
    val normalizedTitle = title.trim.toLowerCase
    club.copy(honors = club.honors.filterNot(_.title.trim.toLowerCase == normalizedTitle))

  def grantAdmin(club: Club, playerId: PlayerId): Club =
    club.copy(admins = (club.admins :+ playerId).distinct)

  def revokeAdmin(club: Club, playerId: PlayerId): Club =
    club.copy(admins = club.admins.filterNot(_ == playerId))

  def setInternalTitle(club: Club, assignment: ClubTitleAssignment): Club =
    club.copy(
      titleAssignments =
        club.titleAssignments.filterNot(_.playerId == assignment.playerId) :+ assignment
    )

  def clearInternalTitle(club: Club, playerId: PlayerId): Club =
    club.copy(
      titleAssignments = club.titleAssignments.filterNot(_.playerId == playerId)
    )

  def updateRecruitmentPolicy(club: Club, policy: ClubRecruitmentPolicy): Club =
    validateRecruitmentPolicy(policy)
    club.copy(recruitmentPolicy = policy)

  def submitApplication(club: Club, application: ClubMembershipApplication): Club =
    club.copy(
      membershipApplications =
        club.membershipApplications.filterNot(_.id == application.id) :+ application
    )

  def reviewApplication(
      club: Club,
      applicationId: MembershipApplicationId,
      review: ClubMembershipApplication => ClubMembershipApplication
  ): Club =
    club.copy(
      membershipApplications = club.membershipApplications.map { application =>
        if application.id == applicationId then review(application) else application
      }
    )

  def findApplication(club: Club, applicationId: MembershipApplicationId): Option[ClubMembershipApplication] =
    club.membershipApplications.find(_.id == applicationId)

  def updatePowerRating(club: Club, rating: Double): Club =
    club.copy(powerRating = rating)

  def contributionOf(club: Club, playerId: PlayerId): Int =
    club.memberContributions.find(_.playerId == playerId).map(_.amount).getOrElse(0)

  def rankFor(club: Club, playerId: PlayerId): Option[ClubRankNode] =
    Option.when(club.members.contains(playerId)) {
      club.rankTree
        .filter(_.minimumContribution <= contributionOf(club, playerId))
        .lastOption
        .getOrElse(club.rankTree.head)
    }

  def privilegesFor(club: Club, playerId: PlayerId): Vector[ClubPrivilegeCode] =
    rankFor(club, playerId).map(_.privileges).getOrElse(Vector.empty)

  def hasPrivilege(club: Club, playerId: PlayerId, privilege: ClubPrivilegeCode): Boolean =
    privilegesFor(club, playerId).contains(privilege)

  def memberPrivilegeSnapshot(club: Club, playerId: PlayerId): Option[ClubMemberPrivilegeSnapshot] =
    rankFor(club, playerId).map { rank =>
      ClubMemberPrivilegeSnapshot(
        playerId = playerId,
        contribution = contributionOf(club, playerId),
        rankCode = rank.code,
        rankLabel = rank.label,
        privileges = rank.privileges,
        isAdmin = club.admins.contains(playerId),
        internalTitle = club.titleAssignments.find(_.playerId == playerId).map(_.title)
      )
    }

  def memberPrivilegeSnapshots(club: Club): Vector[ClubMemberPrivilegeSnapshot] =
    club.members.flatMap(memberPrivilegeSnapshot(club, _)).sortBy(snapshot =>
      (-snapshot.contribution, snapshot.rankCode, snapshot.playerId.value)
    )

  def updateMemberContribution(club: Club, contribution: ClubMemberContribution): Club =
    require(
      club.members.contains(contribution.playerId),
      s"Player ${contribution.playerId.value} must be a club member to track contributions"
    )
    require(contribution.amount >= 0, "Club member contribution cannot be negative")
    club.copy(
      memberContributions =
        club.memberContributions.filterNot(_.playerId == contribution.playerId) :+ contribution
    )

  def updateRankTree(club: Club, nodes: Vector[ClubRankNode]): Club =
    require(nodes.nonEmpty, "Club rank tree cannot be empty")
    val normalizedNodes = nodes.map { node =>
      val normalizedPrivileges = node.privileges
        .distinct
        .sortBy(ClubPrivilegeCode.toString)
      node.copy(
        code = node.code.trim,
        label = node.label.trim,
        privileges = normalizedPrivileges
      )
    }
    require(
      normalizedNodes.map(_.code.trim.toLowerCase).distinct.size == normalizedNodes.size,
      "Club rank node codes must be unique"
    )
    require(
      normalizedNodes.map(_.label.trim.toLowerCase).distinct.size == normalizedNodes.size,
      "Club rank node labels must be unique"
    )
    require(
      normalizedNodes.forall(node => node.code.trim.nonEmpty && node.label.trim.nonEmpty),
      "Club rank node code and label cannot be empty"
    )
    require(
      normalizedNodes.forall(_.minimumContribution >= 0),
      "Club rank node minimum contribution cannot be negative"
    )
    val normalized = normalizedNodes.sortBy(node => (node.minimumContribution, node.code.trim.toLowerCase))
    require(
      normalized.head.minimumContribution == 0,
      "Club rank tree must start at minimum contribution 0"
    )
    club.copy(rankTree = normalized)

  def upsertRelation(club: Club, relation: ClubRelation): Club =
    club.copy(
      relations = club.relations.filterNot(_.targetClubId == relation.targetClubId) :+ relation
    )

  def removeRelation(club: Club, targetClubId: ClubId): Club =
    club.copy(relations = club.relations.filterNot(_.targetClubId == targetClubId))

  def dissolve(club: Club, by: PlayerId, at: Instant): Club =
    club.copy(
      dissolvedAt = Some(at),
      dissolvedBy = Some(by)
    )

  private def validateRecruitmentPolicy(policy: ClubRecruitmentPolicy): Unit =
    policy.requirementsText.foreach(text =>
      require(text.trim.nonEmpty, "Club recruitment requirements text cannot be empty")
    )
    policy.expectedReviewSlaHours.foreach(hours =>
      require(hours > 0, "Club recruitment expected review SLA must be positive")
    )
