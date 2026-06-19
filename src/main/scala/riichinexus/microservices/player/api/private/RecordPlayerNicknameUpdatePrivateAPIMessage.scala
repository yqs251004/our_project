package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供认证流程校验后记录玩家昵称更新。 */
final case class RecordPlayerNicknameUpdatePrivateAPIMessage(
    playerId: PlayerId,
    nickname: String
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        PlayerDomainRecord.save(context, player.copy(nickname = nickname)).map(Some(_))
      case None =>
        IO.pure(None)
    }
