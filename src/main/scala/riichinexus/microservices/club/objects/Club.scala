package riichinexus.microservices.club.objects

import riichinexus.domain.model.{Club as DomainClub}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class Club(
    id: String,
    name: String,
    members: Vector[String],
    admins: Vector[String],
    powerRating: Double,
    treasuryBalance: Long,
    totalPoints: Int,
    pointPool: Int,
    rankTree: Vector[ClubRankNodeView],
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
      rankTree = club.rankTree.map(ClubRankNodeView.fromDomain),
      relations = club.relations.map(ClubRelation.fromDomain),
      dissolvedAt = club.dissolvedAt.map(_.toString)
    )
