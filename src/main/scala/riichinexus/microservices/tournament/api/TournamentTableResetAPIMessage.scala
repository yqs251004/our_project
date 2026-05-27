package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentTableResetAPIMessage(tableId: String, request: ForceResetTableRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(context.principal(request.operator))
      resetAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = ResetTableCommand(TableId(tableId), actor, request.note, resetAt)
      table <- IO {
        module.transactionManager.inTransaction {
          resetTable(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def resetTable(connection: java.sql.Connection, module: TournamentModuleContext, command: ResetTableCommand): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ResetTableState,
        tournamentId = Some(table.tournamentId)
      )
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, table.forceReset(command.note, command.resetAt))
    }

  private final case class ResetTableCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      note: String,
      resetAt: Instant
  )
