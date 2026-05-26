package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentStartAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = StartTournamentCommand(TournamentId(tournamentId), actor)
      tournament <- IO {
        module.transactionManager.inTransaction {
          startTournament(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def startTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: StartTournamentCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      )
      ensureTournamentHasParticipants(tournament, command.tournamentId)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.start)
    }

  private def ensureTournamentHasParticipants(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot start without participants"
      )

  private final case class StartTournamentCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
