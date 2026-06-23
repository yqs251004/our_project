package riichinexus.microservices.club.api.profile.`private`
import cats.effect.IO
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端结算或统计流程应用俱乐部积分变化。 */
final case class ApplyClubPointDeltaPrivateAPIMessage(
    clubId: ClubId,
    points: Int
) extends APIMessage[Option[Club]]:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        SaveClubPrivateAPIMessage(ClubFunctions.addPoints(club, points)).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
