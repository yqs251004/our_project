package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerRoleFunctions}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供俱乐部或平台管理流程校验后记录玩家离开俱乐部。 */
final case class RecordPlayerClubRemovalPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        val updated = PlayerRoleFunctions.revokeClubAdmin(
          PlayerClubBindingFunctions.leaveClub(player, clubId),
          clubId
        )
        PlayerDomainRecord.save(context, updated).map(Some(_))
      case None =>
        IO.pure(None)
    }
