package riichinexus.microservices.club.domain.functions

import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.ClubMemberPrivilegeSnapshot
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationResponse
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView

private[club] object ClubViewFunctions:
  def clubView(club: Club): ClubView =
    ClubView(
      id = club.id.value,
      name = club.name,
      members = club.members.map(_.value),
      admins = club.admins.map(_.value),
      powerRating = club.powerRating,
      treasuryBalance = club.treasuryBalance,
      totalPoints = club.totalPoints,
      pointPool = club.pointPool,
      rankTree = club.rankTree,
      relations = club.relations.map(relationView),
      dissolvedAt = club.dissolvedAt.map(_.toString)
    )

  def relationView(relation: ClubRelation): ClubRelationView =
    ClubRelationView(
      targetClubId = relation.targetClubId.value,
      relation = relation.relation
    )

  def memberPrivilegeSnapshotView(snapshot: ClubMemberPrivilegeSnapshot): ClubMemberPrivilegeSnapshotView =
    ClubMemberPrivilegeSnapshotView(
      playerId = snapshot.playerId.value,
      contribution = snapshot.contribution,
      rankCode = snapshot.rankCode,
      rankLabel = snapshot.rankLabel,
      privileges = snapshot.privileges,
      isAdmin = snapshot.isAdmin,
      internalTitle = snapshot.internalTitle
    )

  def membershipApplicationResponse(application: ClubMembershipApplication): ClubMembershipApplicationResponse =
    ClubMembershipApplicationResponse(
      id = application.id.value,
      playerId = application.playerId.map(_.value),
      displayName = application.displayName,
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId
    )
