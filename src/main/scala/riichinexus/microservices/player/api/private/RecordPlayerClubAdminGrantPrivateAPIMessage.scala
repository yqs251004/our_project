package riichinexus.microservices.player.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供俱乐部流程校验后记录玩家俱乐部管理员授权。 */
final case class RecordPlayerClubAdminGrantPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId,
    grantedAt: Instant,
    grantedBy: Option[PlayerId]
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        SavePlayerPrivateAPIMessage(
          PlayerRoleFunctions.grantRole(
            player,
            RoleGrant(Role.ClubAdmin, grantedAt = grantedAt, grantedBy = grantedBy, clubId = Some(clubId))
          )
        ).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
