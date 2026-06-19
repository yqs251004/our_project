package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerStatusFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供平台管理流程校验后记录玩家封禁。 */
final case class RecordPlayerBanPrivateAPIMessage(
    playerId: PlayerId,
    reason: String
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    require(reason.trim.nonEmpty, "Ban reason cannot be empty")

    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        PlayerDomainRecord.save(context, PlayerStatusFunctions.ban(player, reason)).map(Some(_))
      case None =>
        IO.pure(None)
    }
