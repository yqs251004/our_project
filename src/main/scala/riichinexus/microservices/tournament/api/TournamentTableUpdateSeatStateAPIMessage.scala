package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableSeat}
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{TournamentTableView, UpdateTableSeatStateRequest}

/** 管理指定座位的准备和断线状态。 */
final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      command = updateSeatStateCommand(actor)
      table <- updateSeatState(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tableView(table)

  private def updateSeatStateCommand(actor: AccessPrincipalPrivateView): UpdateSeatStateCommand =
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

  private def updateSeatState(context: ApiPlanContext, command: UpdateSeatStateCommand): IO[Option[Table]] =
    loadTable(context, command.tableId).flatMap {
      case Some(table) => updateLoadedTableSeatState(context, table, command).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(context.connection, tableId)
    }

  private def updateLoadedTableSeatState(
      context: ApiPlanContext,
      table: Table,
      command: UpdateSeatStateCommand
  ): IO[Table] =
    val targetSeat = TableFunctions.seatFor(table, command.seat)
    for
      _ <- requireSeatStatePermission(context, command.actor, table, targetSeat)
      updatedTable <- IO.blocking(updateAndSaveSeatState(context.connection, table, command))
    yield updatedTable

  private def updateAndSaveSeatState(
      connection: java.sql.Connection,
      table: Table,
      command: UpdateSeatStateCommand
  ): Table =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
      connection,
      TableFunctions.updateSeatState(
        table,
        targetSeat = command.seat,
        ready = command.ready,
        disconnected = command.disconnected,
        note = seatStateNote(command.actor, command.seat, command.note)
      )
    )

  private def requireSeatStatePermission(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      table: Table,
      targetSeat: TableSeat
  ): IO[Unit] =
    RequirePermissionPrivateAPIMessage(
      actor,
      Permission.ManageTableSeatState,
      tournamentId = Some(table.tournamentId),
      subjectPlayerId = Some(targetSeat.playerId)
    ).plan(context)

  private def seatStateNote(actor: AccessPrincipalPrivateView, seat: SeatWind, note: Option[String]): Option[String] =
    note.map(message => s"${actor.displayName} updated ${seat.toString} seat state: $message")

  /** 管理员更新牌桌指定座位状态时使用的内部命令。 */
  private final case class UpdateSeatStateCommand(
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipalPrivateView,
      ready: Option[Boolean],
      disconnected: Option[Boolean],
      note: Option[String]
  )
