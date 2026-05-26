package riichinexus.microservices.club.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.model.{Club as DomainClub}
import upickle.default.*

final case class ClubView(
    id: String,
    name: String,
    members: Vector[String],
    admins: Vector[String],
    powerRating: Double,
    treasuryBalance: Long,
    totalPoints: Int,
    pointPool: Int,
    rankTree: Vector[ClubRankNodeView],
    relations: Vector[ClubRelationView],
    dissolvedAt: Option[String]
) derives ReadWriter

object ClubView:
  def fromDomain(club: DomainClub): ClubView =
    ClubView(
      id = club.id.value,
      name = club.name,
      members = club.members.map(_.value),
      admins = club.admins.map(_.value),
      powerRating = club.powerRating,
      treasuryBalance = club.treasuryBalance,
      totalPoints = club.totalPoints,
      pointPool = club.pointPool,
      rankTree = club.rankTree.map(ClubRankNodeView.fromDomain),
      relations = club.relations.map(ClubRelationView.fromDomain),
      dissolvedAt = club.dissolvedAt.map(_.toString)
    )
