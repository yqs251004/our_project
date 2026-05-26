package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import upickle.default.*

final case class TournamentStageCreateAPIMessage(tournamentId: String, request: CreateTournamentStageRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(request.operator.map(context.principal).getOrElse(AccessPrincipal.system))
      module = context.support.tournamentModule
      command = CreateStageCommand(
        tournamentId = TournamentId(tournamentId),
        actor = actor,
        stage = request.toStage
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          createStage(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def createStage(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: CreateStageCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      ensureStageCanBeAdded(module, tournament, command)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.addStage(TournamentRuntimeDefaults.normalizeStage(command.stage)))
    }

  private def ensureStageCanBeAdded(
      module: TournamentModuleContext,
      tournament: Tournament,
      command: CreateStageCommand
  ): Unit =
    if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
      throw IllegalArgumentException(
        s"Cannot add stages to tournament ${command.tournamentId.value} in status ${tournament.status}"
      )
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

  private final case class CreateStageCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipal,
      stage: TournamentStage
  )
