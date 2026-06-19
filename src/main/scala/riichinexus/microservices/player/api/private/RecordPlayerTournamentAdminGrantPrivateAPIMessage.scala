package riichinexus.microservices.player.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供赛事流程校验后记录玩家赛事管理员授权。 */
final case class RecordPlayerTournamentAdminGrantPrivateAPIMessage(
    playerId: PlayerId,
    tournamentId: TournamentId,
    grantedAt: Instant,
    grantedBy: Option[PlayerId]
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        SavePlayerPrivateAPIMessage(
          PlayerRoleFunctions.grantRole(
            player,
            RoleGrant(
              Role.TournamentAdmin,
              grantedAt = grantedAt,
              grantedBy = grantedBy,
              tournamentId = Some(tournamentId)
            )
          )
        ).plan(context).map(Some(_))
      case None =>
        IO.pure(None)
    }
