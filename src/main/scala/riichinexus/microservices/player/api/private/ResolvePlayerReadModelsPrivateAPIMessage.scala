package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.functions.PlayerPrivateViewFunctions
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端服务按 id 批量读取玩家 private read model。 */
final case class ResolvePlayerReadModelsPrivateAPIMessage(
    playerIds: Vector[PlayerId]
) extends APIMessage[Vector[PlayerPrivateView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[PlayerPrivateView]] =
    IO.blocking(PlayerTable.findByIds(context.connection, playerIds).map(PlayerPrivateViewFunctions.fromPlayer))
