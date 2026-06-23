package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供俱乐部流程校验后记录玩家加入俱乐部。 */
final case class RecordPlayerClubJoinPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    IO.blocking(PlayerTable.findById(context.connection, playerId)).flatMap {
      case Some(player) =>
        IO.blocking(PlayerTable.save(context.connection, PlayerClubBindingFunctions.joinClub(player, clubId))).map(Some(_))
      case None =>
        IO.pure(None)
    }
