package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.*

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
    rankTree: Vector[ClubRankNode] = Club.defaultRankTree,
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
) derives CanEqual:
  def addMember(playerId: PlayerId): Club =
    if members.contains(playerId) then this
    else copy(members = members :+ playerId)

  def removeMember(playerId: PlayerId): Club =
    copy(
      members = members.filterNot(_ == playerId),
      admins = admins.filterNot(_ == playerId),
      memberContributions = memberContributions.filterNot(_.playerId == playerId),
      titleAssignments = titleAssignments.filterNot(_.playerId == playerId)
    )

  def addPoints(points: Int): Club =
    copy(
      totalPoints = totalPoints + points,
      pointPool = pointPool + points
    )

  def adjustTreasury(delta: Long): Club =
    require(treasuryBalance + delta >= 0L, "Club treasury balance cannot be negative")
    copy(treasuryBalance = treasuryBalance + delta)

  def adjustPointPool(delta: Int): Club =
    require(pointPool + delta >= 0, "Club point pool cannot be negative")
    copy(pointPool = pointPool + delta)

  def addHonor(honor: ClubHonor): Club =
    val normalizedTitle = honor.title.trim.toLowerCase
    copy(
      honors =
        honors.filterNot(_.title.trim.toLowerCase == normalizedTitle) :+ honor
    )

  def removeHonor(title: String): Club =
    val normalizedTitle = title.trim.toLowerCase
    copy(honors = honors.filterNot(_.title.trim.toLowerCase == normalizedTitle))

  def grantAdmin(playerId: PlayerId): Club =
    copy(admins = (admins :+ playerId).distinct)

  def revokeAdmin(playerId: PlayerId): Club =
    copy(admins = admins.filterNot(_ == playerId))

  def setInternalTitle(assignment: ClubTitleAssignment): Club =
    copy(
      titleAssignments =
        titleAssignments.filterNot(_.playerId == assignment.playerId) :+ assignment
    )

  def clearInternalTitle(playerId: PlayerId): Club =
    copy(
      titleAssignments = titleAssignments.filterNot(_.playerId == playerId)
    )

  def updateRecruitmentPolicy(policy: ClubRecruitmentPolicy): Club =
    copy(recruitmentPolicy = policy)

  def submitApplication(application: ClubMembershipApplication): Club =
    copy(
      membershipApplications =
        membershipApplications.filterNot(_.id == application.id) :+ application
    )

  def reviewApplication(
      applicationId: MembershipApplicationId,
      review: ClubMembershipApplication => ClubMembershipApplication
  ): Club =
    copy(
      membershipApplications = membershipApplications.map { application =>
        if application.id == applicationId then review(application) else application
      }
    )

  def findApplication(applicationId: MembershipApplicationId): Option[ClubMembershipApplication] =
    membershipApplications.find(_.id == applicationId)

  def updatePowerRating(rating: Double): Club =
    copy(powerRating = rating)

  def contributionOf(playerId: PlayerId): Int =
    memberContributions.find(_.playerId == playerId).map(_.amount).getOrElse(0)

  def rankFor(playerId: PlayerId): Option[ClubRankNode] =
    Option.when(members.contains(playerId)) {
      rankTree
        .filter(_.minimumContribution <= contributionOf(playerId))
        .lastOption
        .getOrElse(rankTree.head)
    }

  def privilegesFor(playerId: PlayerId): Vector[String] =
    rankFor(playerId).map(_.privileges).getOrElse(Vector.empty)

  def hasPrivilege(playerId: PlayerId, privilege: String): Boolean =
    privilegesFor(playerId).contains(ClubPrivilege.normalize(privilege))

  def memberPrivilegeSnapshot(playerId: PlayerId): Option[ClubMemberPrivilegeSnapshot] =
    rankFor(playerId).map { rank =>
      ClubMemberPrivilegeSnapshot(
        playerId = playerId,
        contribution = contributionOf(playerId),
        rankCode = rank.code,
        rankLabel = rank.label,
        privileges = rank.privileges,
        isAdmin = admins.contains(playerId),
        internalTitle = titleAssignments.find(_.playerId == playerId).map(_.title)
      )
    }

  def memberPrivilegeSnapshots: Vector[ClubMemberPrivilegeSnapshot] =
    members.flatMap(memberPrivilegeSnapshot).sortBy(snapshot =>
      (-snapshot.contribution, snapshot.rankCode, snapshot.playerId.value)
    )

  def updateMemberContribution(contribution: ClubMemberContribution): Club =
    require(
      members.contains(contribution.playerId),
      s"Player ${contribution.playerId.value} must be a club member to track contributions"
    )
    copy(
      memberContributions =
        memberContributions.filterNot(_.playerId == contribution.playerId) :+ contribution
    )

  def updateRankTree(nodes: Vector[ClubRankNode]): Club =
    require(nodes.nonEmpty, "Club rank tree cannot be empty")
    val normalizedNodes = nodes.map { node =>
      val normalizedPrivileges = node.privileges
        .map(ClubPrivilegeRegistry.requireSupported)
        .filter(_.nonEmpty)
        .distinct
        .sorted
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
    copy(rankTree = normalized)

  def upsertRelation(relation: ClubRelation): Club =
    copy(
      relations = relations.filterNot(_.targetClubId == relation.targetClubId) :+ relation
    )

  def removeRelation(targetClubId: ClubId): Club =
    copy(relations = relations.filterNot(_.targetClubId == targetClubId))

  def dissolve(by: PlayerId, at: Instant): Club =
    copy(
      dissolvedAt = Some(at),
      dissolvedBy = Some(by)
    )

object Club:
  val defaultRankTree: Vector[ClubRankNode] =
    Vector(
      ClubRankNode("rookie", "萌新", minimumContribution = 0),
      ClubRankNode("member", "同伴", minimumContribution = 500),
      ClubRankNode("core", "主力", minimumContribution = 1500),
      ClubRankNode("ace", "王牌", minimumContribution = 3000)
    )
