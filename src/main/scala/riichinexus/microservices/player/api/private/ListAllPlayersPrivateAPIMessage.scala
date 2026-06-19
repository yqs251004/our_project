package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.functions.PlayerPrivateViewFunctions
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端服务读取全部玩家 private read model。 */
final case class ListAllPlayersPrivateAPIMessage() extends APIMessage[Vector[PlayerPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[PlayerPrivateView]] =
    IO.blocking(PlayerTable.findAll(context.connection).map(PlayerPrivateViewFunctions.fromPlayer))
