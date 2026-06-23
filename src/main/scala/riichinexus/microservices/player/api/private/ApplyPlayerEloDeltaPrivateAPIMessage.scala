package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.player.domain.functions.PlayerRatingFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端统计流程应用玩家 Elo 变化。 */
final case class ApplyPlayerEloDeltaPrivateAPIMessage(
    playerId: PlayerId,
    delta: Int
) extends APIMessage[Unit]:

  override def plan(context: ApiPlanContext): IO[Unit] =
    IO.blocking(PlayerTable.findById(context.connection, playerId)).flatMap {
      case Some(player) =>
        SavePlayerPrivateAPIMessage(PlayerRatingFunctions.applyElo(player, delta)).plan(context).map(_ => ())
      case None =>
        IO.unit
    }
