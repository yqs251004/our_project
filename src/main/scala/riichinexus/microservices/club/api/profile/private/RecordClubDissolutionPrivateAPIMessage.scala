package riichinexus.microservices.club.api.profile.`private`
import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供平台管理流程校验后记录俱乐部解散。 */
final case class RecordClubDissolutionPrivateAPIMessage(
    clubId: ClubId,
    by: PlayerId,
    at: Instant
) extends APIMessage[Option[Club]]:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        SaveClubPrivateAPIMessage(ClubFunctions.dissolve(club, by, at)).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
