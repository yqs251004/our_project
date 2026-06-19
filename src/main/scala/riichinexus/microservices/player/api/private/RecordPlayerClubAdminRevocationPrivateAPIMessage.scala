package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供俱乐部流程校验后记录玩家俱乐部管理员撤销。 */
final case class RecordPlayerClubAdminRevocationPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        PlayerDomainRecord.save(context, PlayerRoleFunctions.revokeClubAdmin(player, clubId)).map(Some(_))
      case None =>
        IO.pure(None)
    }
