package riichinexus.microservices.player.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.auth.objects.authorization.`private`.RoleGrant
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供赛事流程校验后记录玩家赛事管理员授权。 */
final case class RecordPlayerTournamentAdminGrantPrivateAPIMessage(
    playerId: PlayerId,
    tournamentId: TournamentId,
    grantedAt: Instant,
    grantedBy: Option[PlayerId]
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    IO.blocking(PlayerTable.findById(context.connection, playerId)).flatMap {
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
