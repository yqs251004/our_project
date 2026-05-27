package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.{TournamentOperationViewAssembler, TournamentStageTableScheduler}
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentStageScheduleTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = ScheduleStageTablesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor
      )
      scheduledTables <- IO {
        module.transactionManager.inTransaction {
          scheduleTables(context.connection, module, command)
        }
      }
      view <- IO {
        TournamentOperationViewAssembler
        .mutationView(context.connection, module, command.tournamentId, scheduledTables)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def scheduleTables(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: ScheduleStageTablesCommand
  ): Vector[Table] =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    TournamentStageTableScheduler.schedule(
      connection = connection,
      module = module,
      tournamentId = command.tournamentId,
      stageId = command.stageId
    )

  private final case class ScheduleStageTablesCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal
  )
