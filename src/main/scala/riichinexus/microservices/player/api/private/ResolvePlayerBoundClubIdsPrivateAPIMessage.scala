package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务解析玩家绑定的俱乐部 id。 */
final case class ResolvePlayerBoundClubIdsPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[ClubId]]:

  override def plan(context: ApiPlanContext): IO[Vector[ClubId]] =
    IO.blocking(PlayerTable.findById(context.connection, playerId)).map {
      case Some(player) => PlayerClubBindingFunctions.boundClubIds(player)
      case None         => Vector.empty
    }
