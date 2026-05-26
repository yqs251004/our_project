package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.SeatWind
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import upickle.default.*

final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(context.principal(request.operator))
      module = context.support.tournamentModule
      command = UpdateSeatStateCommand(
        tableId = TableId(tableId),
        seat = SeatWind.valueOf(seat),
        actor = actor,
        ready = request.ready,
        disconnected = request.disconnected,
        note = request.note
      )
      table <- IO {
        module.transactionManager.inTransaction {
          updateSeatState(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def updateSeatState(connection: java.sql.Connection, module: TournamentModuleContext, command: UpdateSeatStateCommand): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      val targetSeat = table.seatFor(command.seat)
      requireSeatStatePermission(module, command.actor, table, targetSeat)
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, 
        table.updateSeatState(
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
    module.authorizationService.requirePermission(
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
