package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供俱乐部流程校验后记录玩家加入俱乐部。 */
final case class RecordPlayerClubJoinPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        PlayerDomainRecord.save(context, PlayerClubBindingFunctions.joinClub(player, clubId)).map(Some(_))
      case None =>
        IO.pure(None)
    }
