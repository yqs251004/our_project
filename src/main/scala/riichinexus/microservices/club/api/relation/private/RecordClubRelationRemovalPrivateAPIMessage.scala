package riichinexus.microservices.club.api.relation.`private`
import cats.effect.IO
import riichinexus.microservices.club.api.profile.`private`.ResolveClubPrivateAPIMessage
import riichinexus.microservices.club.api.profile.`private`.SaveClubPrivateAPIMessage
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供平台管理流程记录俱乐部关系移除。 */
final case class RecordClubRelationRemovalPrivateAPIMessage(
    clubId: ClubId,
    targetClubId: ClubId
) extends APIMessage[Option[Club]]:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        SaveClubPrivateAPIMessage(ClubFunctions.removeRelation(club, targetClubId)).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
