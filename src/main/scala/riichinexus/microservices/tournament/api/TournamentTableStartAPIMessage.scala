package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TableFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      startedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = StartTableCommand(TableId(tableId), actor, startedAt)
      table <- IO.blocking {
        module.transactionManager.inTransaction {
          startTable(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(AuthAccessPrincipalResolver.principal(context, _))
      .getOrElse(AccessPrincipalFunctions.system)

  private def startTable(connection: java.sql.Connection, module: TournamentModuleContext, command: StartTableCommand): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      AuthorizationPolicyFunctions.requirePermission(module.authorizationService, 
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      )
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, TableFunctions.start(table, command.startedAt))
    }

  private final case class StartTableCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      startedAt: Instant
  )
