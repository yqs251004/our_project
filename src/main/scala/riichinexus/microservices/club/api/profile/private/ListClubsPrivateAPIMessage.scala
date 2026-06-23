package riichinexus.microservices.club.api.profile.`private`
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 供后端服务读取俱乐部 domain 列表。 */
final case class ListClubsPrivateAPIMessage(
    activeOnly: Boolean = false,
    joinableOnly: Boolean = false,
    memberId: Option[PlayerId] = None,
    adminId: Option[PlayerId] = None,
    name: Option[String] = None
) extends APIMessage[Vector[Club]]:

  override def plan(context: ApiPlanContext): IO[Vector[Club]] =
    for
      clubs <- IO.blocking {
        ClubTable.findFiltered(
          context.connection,
          activeOnly = activeOnly,
          joinableOnly = joinableOnly,
          memberId = memberId,
          adminId = adminId,
          name = name
        )
      }
    yield clubs
