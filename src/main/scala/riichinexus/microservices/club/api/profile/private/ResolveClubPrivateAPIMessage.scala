package riichinexus.microservices.club.api.profile.`private`
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 供后端服务按 id 解析俱乐部 domain 对象。 */
final case class ResolveClubPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Option[Club]]:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    for
      club <- IO.blocking(ClubTable.findById(context.connection, clubId))
    yield club
