package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供赛事流程校验后记录玩家赛事管理员撤销。 */
final case class RecordPlayerTournamentAdminRevocationPrivateAPIMessage(
    playerId: PlayerId,
    tournamentId: TournamentId
) extends APIMessage[Option[Player]]:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerDomainRecord.find(context, playerId).flatMap {
      case Some(player) =>
        PlayerDomainRecord.save(context, PlayerRoleFunctions.revokeTournamentAdmin(player, tournamentId)).map(Some(_))
      case None =>
        IO.pure(None)
    }
