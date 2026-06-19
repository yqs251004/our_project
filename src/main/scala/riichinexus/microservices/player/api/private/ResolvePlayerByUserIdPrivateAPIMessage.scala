package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}

import riichinexus.microservices.player.domain.functions.PlayerPrivateViewFunctions
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.ReadWriter

/** 供后端服务按用户 id 解析玩家 private read model。 */
final case class ResolvePlayerByUserIdPrivateAPIMessage(
    userId: String
) extends APIMessage[Option[PlayerPrivateView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[PlayerPrivateView]] =
    IO.blocking(PlayerTable.findByUserId(context.connection, userId).map(PlayerPrivateViewFunctions.fromPlayer))
