package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端结算或统计流程应用俱乐部积分变化。 */
final case class ApplyClubPointDeltaPrivateAPIMessage(
    clubId: ClubId,
    points: Int
) extends APIMessage[Option[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        SaveClubPrivateAPIMessage(ClubFunctions.addPoints(club, points)).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
