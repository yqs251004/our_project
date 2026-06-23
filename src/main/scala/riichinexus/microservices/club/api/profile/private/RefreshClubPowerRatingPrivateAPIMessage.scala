package riichinexus.microservices.club.api.profile.`private`
import cats.effect.IO
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubPowerRatingService
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端统计流程刷新俱乐部战力评分。 */
final case class RefreshClubPowerRatingPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Option[Club]]:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
      case Some(club) =>
        ResolvePlayersPrivateAPIMessage(club.members).plan(context).flatMap { players =>
          val playersById = players.map(player => player.id -> player).toMap
          SaveClubPrivateAPIMessage(
            ClubFunctions.updatePowerRating(
              club,
              ClubPowerRatingService.calculate(club, playerId => playersById.get(playerId))
            )
          ).plan(context).map(Some(_))
        }
      case None =>
        IO.pure(None)
    }
