package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.`private`.{ClubPrivateView, ClubRelationPrivateView}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务按 id 批量读取俱乐部 private read model。 */
final case class ResolveClubReadModelsPrivateAPIMessage(
    clubIds: Vector[ClubId]
) extends APIMessage[Vector[ClubPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivateView]] =
    IO.blocking(ClubTable.findByIds(context.connection, clubIds).map(toPrivateView))

  private def toPrivateView(club: Club): ClubPrivateView =
    ClubPrivateView(
      id = club.id,
      name = club.name,
      creator = club.creator,
      createdAt = club.createdAt,
      members = club.members,
      admins = club.admins,
      relations = club.relations.map(relation => ClubRelationPrivateView(relation.targetClubId, relation.relation)),
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      treasuryBalance = club.treasuryBalance,
      dissolvedAt = club.dissolvedAt,
      dissolvedBy = club.dissolvedBy
    )
