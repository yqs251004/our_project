package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.functions.PlayerRatingFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端统计流程应用玩家 Elo 变化。 */
final case class ApplyPlayerEloDeltaPrivateAPIMessage(
    playerId: PlayerId,
    delta: Int
) extends APIMessage[Unit] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Unit] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        SavePlayerPrivateAPIMessage(PlayerRatingFunctions.applyElo(player, delta)).plan(context).map(_ => ())
      case None =>
        IO.unit
    }
