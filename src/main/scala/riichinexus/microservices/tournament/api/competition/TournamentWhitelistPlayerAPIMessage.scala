package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView

/** 将选手加入赛事白名单。 */
final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedPlayerId = PlayerId(playerId)
      tournament <- whitelistPlayer(context, requestedTournamentId, requestedPlayerId, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def whitelistPlayer(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(tournamentId)).plan(context)
      tournament <- IO.blocking {
        ensurePlayerCanBeWhitelisted(player, playerId)
        TournamentTable.findById(connection, tournamentId).map { tournament =>
          TournamentTable.save(connection, TournamentFunctions.whitelistPlayer(tournament, playerId))
        }
      }
    yield tournament

  private def ensurePlayerCanBeWhitelisted(player: PlayerPrivateView, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${playerId.value} cannot be whitelisted")

