package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerRoleFunctions}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供俱乐部或平台管理流程校验后记录玩家离开俱乐部。 */
final case class RecordPlayerClubRemovalPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    IO.blocking(PlayerTable.findById(context.connection, playerId)).flatMap {
      case Some(player) =>
        val updated = PlayerRoleFunctions.revokeClubAdmin(
          PlayerClubBindingFunctions.leaveClub(player, clubId),
          clubId
        )
        IO.blocking(PlayerTable.save(context.connection, updated)).map(Some(_))
      case None =>
        IO.pure(None)
    }
