package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.`private`.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TournamentStageTableScheduler
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import upickle.default.*

final case class TournamentStageScheduleTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = ScheduleStageTablesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor
      )
      scheduledTables <- IO.blocking {
        module.transactionManager.inTransaction {
          scheduleTables(context.connection, module, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler
        .mutationView(context.connection, module, command.tournamentId, scheduledTables)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(AuthAccessPrincipalResolver.principal(context, _))
      .getOrElse(AccessPrincipalFunctions.system)

  private def scheduleTables(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: ScheduleStageTablesCommand
  ): Vector[Table] =
    AuthorizationPolicyFunctions.requirePermission(module.authorizationService, 
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
