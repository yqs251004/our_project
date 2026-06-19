package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.`private`.{ClubPrivateView, ClubRelationPrivateView}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务按筛选条件读取俱乐部 private read model。 */
final case class ListClubReadModelsPrivateAPIMessage(
    activeOnly: Boolean = false,
    joinableOnly: Boolean = false,
    memberId: Option[PlayerId] = None,
    adminId: Option[PlayerId] = None,
    name: Option[String] = None
) extends APIMessage[Vector[ClubPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivateView]] =
    IO.blocking {
      ClubTable
        .findFiltered(
          context.connection,
          activeOnly = activeOnly,
          joinableOnly = joinableOnly,
          memberId = memberId,
          adminId = adminId,
          name = name
        )
        .map(toPrivateView)
    }

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
