package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException

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
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat}
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

final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.operatorId)))
      module = context.support.tournamentModule
      command = updateSeatStateCommand(actor)
      table <- IO.blocking {
        module.transactionManager.inTransaction {
          updateSeatState(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def updateSeatStateCommand(actor: AccessPrincipal): UpdateSeatStateCommand =
    validateRequest()
    UpdateSeatStateCommand(
        tableId = TableId(tableId),
        seat = SeatWind.valueOf(seat),
        actor = actor,
        ready = request.ready,
        disconnected = request.disconnected,
        note = request.note
      )

  private def validateRequest(): Unit =
    require(
      request.ready.isDefined || request.disconnected.isDefined,
      "At least one of ready or disconnected must be provided"
    )

  private def updateSeatState(connection: java.sql.Connection, module: TournamentModuleContext, command: UpdateSeatStateCommand): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      val targetSeat = TableFunctions.seatFor(table, command.seat)
      requireSeatStatePermission(module, command.actor, table, targetSeat)
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, 
        TableFunctions.updateSeatState(
          table,
          targetSeat = command.seat,
          ready = command.ready,
          disconnected = command.disconnected,
          note = seatStateNote(command.actor, command.seat, command.note)
        )
      )
    }

  private def requireSeatStatePermission(
      module: TournamentModuleContext,
      actor: AccessPrincipal,
      table: Table,
      targetSeat: TableSeat
  ): Unit =
    AuthorizationPolicyFunctions.requirePermission(module.authorizationService, 
      actor,
      Permission.ManageTableSeatState,
      tournamentId = Some(table.tournamentId),
      subjectPlayerId = Some(targetSeat.playerId)
    )

  private def seatStateNote(actor: AccessPrincipal, seat: SeatWind, note: Option[String]): Option[String] =
    note.map(message => s"${actor.displayName} updated ${seat.toString} seat state: $message")

  private final case class UpdateSeatStateCommand(
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipal,
      ready: Option[Boolean],
      disconnected: Option[Boolean],
      note: Option[String]
  )
