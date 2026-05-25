package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{Club as DomainClub, ClubMemberPrivilegeSnapshot as DomainClubMemberPrivilegeSnapshot, ClubPrivilegeDefinition as DomainClubPrivilegeDefinition, ClubRelation as DomainClubRelation}
import upickle.default.*

final case class ClubRelation(
    relation: String
) derives ReadWriter

object ClubRelation:
  def fromDomain(relation: DomainClubRelation): ClubRelation =
    ClubRelation(relation = relation.relation.toString)

final case class Club(
    id: String,
    name: String,
    members: Vector[String],
    admins: Vector[String],
    powerRating: Double,
    treasuryBalance: Long,
    totalPoints: Int,
    pointPool: Int,
    relations: Vector[ClubRelation],
    dissolvedAt: Option[String]
) derives ReadWriter

object Club:
  def fromDomain(club: DomainClub): Club =
    Club(
      id = club.id.value,
      name = club.name,
      members = club.members.map(_.value),
      admins = club.admins.map(_.value),
      powerRating = club.powerRating,
      treasuryBalance = club.treasuryBalance,
      totalPoints = club.totalPoints,
      pointPool = club.pointPool,
      relations = club.relations.map(ClubRelation.fromDomain),
      dissolvedAt = club.dissolvedAt.map(_.toString)
    )

type PlayerProfileView = riichinexus.microservices.player.objects.apiTypes.PlayerProfileView

final case class ClubPrivilegeDefinition(
    code: String,
    label: String,
    description: String,
    delegatedPermissions: Vector[String]
) derives ReadWriter

object ClubPrivilegeDefinition:
  def fromDomain(definition: DomainClubPrivilegeDefinition): ClubPrivilegeDefinition =
    ClubPrivilegeDefinition(
      code = definition.code,
      label = definition.label,
      description = definition.description,
      delegatedPermissions = definition.delegatedPermissions.map(_.toString)
    )

final case class ClubMemberPrivilegeSnapshot(
    playerId: String,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[String],
    isAdmin: Boolean,
    internalTitle: Option[String]
) derives ReadWriter

object ClubMemberPrivilegeSnapshot:
  def fromDomain(snapshot: DomainClubMemberPrivilegeSnapshot): ClubMemberPrivilegeSnapshot =
    ClubMemberPrivilegeSnapshot(
      playerId = snapshot.playerId.value,
      contribution = snapshot.contribution,
      rankCode = snapshot.rankCode,
      rankLabel = snapshot.rankLabel,
      privileges = snapshot.privileges,
      isAdmin = snapshot.isAdmin,
      internalTitle = snapshot.internalTitle
    )
