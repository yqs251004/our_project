package riichinexus.microservices.club.domain.profile.functions

import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.rankprivilege.model.ClubMemberPrivilegeSnapshot
import riichinexus.microservices.club.domain.relation.model.ClubRelation
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.objects.rankprivilege.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.relation.ClubRelationView

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
