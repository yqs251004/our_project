package riichinexus.microservices.player.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供平台管理或初始化流程校验后记录超级管理员授权。 */
final case class RecordPlayerSuperAdminGrantPrivateAPIMessage(
    playerId: PlayerId,
    grantedAt: Instant,
    grantedBy: Option[PlayerId]
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        SavePlayerPrivateAPIMessage(
          PlayerRoleFunctions.grantRole(player, RoleGrant(Role.SuperAdmin, grantedAt = grantedAt, grantedBy = grantedBy))
        ).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
