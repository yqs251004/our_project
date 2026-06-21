package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentSummaryView

/** 将选手加入赛事白名单。 */
final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      command = WhitelistPlayerCommand(TournamentId(tournamentId), PlayerId(playerId), actor)
      tournament <- whitelistPlayer(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def whitelistPlayer(
      context: ApiPlanContext,
      command: WhitelistPlayerCommand
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      tournament <- IO.blocking {
        ensurePlayerCanBeWhitelisted(player, command.playerId)
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.whitelistPlayer(tournament, command.playerId))
        }
      }
    yield tournament

  private def ensurePlayerCanBeWhitelisted(player: PlayerPrivateView, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${playerId.value} cannot be whitelisted")

  /** 将玩家加入赛事白名单时使用的内部命令。 */
  private final case class WhitelistPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  )
