package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView

/** 开始赛事。 */
final case class TournamentStartAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(requestedTournamentId)).plan(context)
      tournament <- IO.blocking(startTournament(context.connection, requestedTournamentId).getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def startTournament(
      connection: java.sql.Connection,
      tournamentId: TournamentId
  ): Option[Tournament] =
    TournamentTable.findById(connection, tournamentId).map { tournament =>
      ensureTournamentHasParticipants(tournament, tournamentId)
      TournamentTable.save(connection, TournamentFunctions.start(tournament))
    }

  private def ensureTournamentHasParticipants(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot start without participants"
      )

