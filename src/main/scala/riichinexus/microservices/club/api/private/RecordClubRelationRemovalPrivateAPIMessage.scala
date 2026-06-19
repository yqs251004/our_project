package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供平台管理流程记录俱乐部关系移除。 */
final case class RecordClubRelationRemovalPrivateAPIMessage(
    clubId: ClubId,
    targetClubId: ClubId
) extends APIMessage[Option[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        SaveClubPrivateAPIMessage(ClubFunctions.removeRelation(club, targetClubId)).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
