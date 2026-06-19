package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.domain.functions.PlayerPrivateViewFunctions
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.player.tables.players.PlayerTable
/** 供后端服务按 id 批量解析玩家 private read model。 */
final case class ResolvePlayersPrivateAPIMessage(
    playerIds: Vector[PlayerId]
) extends APIMessage[Vector[PlayerPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[PlayerPrivateView]] =
    IO.blocking(PlayerTable.findByIds(context.connection, playerIds).map(PlayerPrivateViewFunctions.fromPlayer))
