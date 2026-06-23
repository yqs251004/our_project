package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableSeat}
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{UpdateTableSeatStateRequest}
import riichinexus.microservices.tournament.objects.stage.table.{TournamentTableView}
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** 管理指定座位的准备和断线状态。 */
final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      requestedTableId <- IO.blocking(TableId(tableId))
      requestedSeat <- IO.blocking(resolveSeat())
      table <- updateSeatState(context, requestedTableId, requestedSeat, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tableView(table)

  private def resolveSeat(): SeatWind =
    validateRequest()
    SeatWind.valueOf(seat)

  private def validateRequest(): Unit =
    require(
      request.ready.isDefined || request.disconnected.isDefined,
      "At least one of ready or disconnected must be provided"
    )

  private def updateSeatState(
      context: ApiPlanContext,
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipalPrivateView
  ): IO[Option[Table]] =
    loadTable(context, tableId).flatMap {
      case Some(table) => updateLoadedTableSeatState(context, table, seat, actor).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      TournamentGameTable.findById(context.connection, tableId)
    }

  private def updateLoadedTableSeatState(
      context: ApiPlanContext,
      table: Table,
      seat: SeatWind,
      actor: AccessPrincipalPrivateView
  ): IO[Table] =
    val targetSeat = TableFunctions.seatFor(table, seat)
    for
      _ <- requireSeatStatePermission(context, actor, table, targetSeat)
      updatedTable <- IO.blocking(updateAndSaveSeatState(context.connection, table, seat, actor))
    yield updatedTable

  private def updateAndSaveSeatState(
      connection: java.sql.Connection,
      table: Table,
      seat: SeatWind,
      actor: AccessPrincipalPrivateView
  ): Table =
    TournamentGameTable.save(
      connection,
      TableFunctions.updateSeatState(
        table,
        targetSeat = seat,
        ready = request.ready,
        disconnected = request.disconnected,
        note = seatStateNote(actor, seat, request.note)
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

