package riichinexus.microservices.club.objects.clubmanagement

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{Club as DomainClub}
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import upickle.default.ReadWriter

/** ClubView 表示俱乐部详情视图，包含成员、管理员、资产、等级树、关系和解散状态。 */

final case class ClubView(
    id: String,
    name: String,
    members: Vector[String],
    admins: Vector[String],
    powerRating: Double,
    treasuryBalance: Long,
    totalPoints: Int,
    pointPool: Int,
    rankTree: Vector[ClubRankNode],
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
      rankTree = club.rankTree,
      relations = club.relations.map(ClubRelationView.fromDomain),
      dissolvedAt = club.dissolvedAt.map(_.toString)
    )
