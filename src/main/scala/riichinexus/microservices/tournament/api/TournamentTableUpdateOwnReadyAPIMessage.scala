package riichinexus.microservices.tournament.api
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


import riichinexus.microservices.tournament.objects.stage.table.TableSeat
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{TournamentTableView, UpdateOwnTableReadyStateRequest}

import upickle.default.ReadWriter

/** 更新当前玩家在牌桌上的准备状态。 */
final case class TournamentTableUpdateOwnReadyAPIMessage(tableId: String, request: UpdateOwnTableReadyStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      command = UpdateOwnReadyCommand(TableId(tableId), actor, request.ready, request.note)
      table <- updateOwnReady(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentTableView.fromDomain(table)

  private def updateOwnReady(context: ApiPlanContext, command: UpdateOwnReadyCommand): IO[Option[Table]] =
    loadTable(context, command.tableId).flatMap {
      case Some(table) => updateLoadedTableOwnReady(context, table, command).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(context.connection, tableId)
    }

  private def updateLoadedTableOwnReady(
      context: ApiPlanContext,
      table: Table,
      command: UpdateOwnReadyCommand
  ): IO[Table] =
    val playerId = requireAuthenticatedPlayer(command.actor)
    val targetSeat = requirePlayerSeat(table, command.tableId, playerId)
    for
      _ <- requireSeatStatePermission(context, command.actor, table, targetSeat)
      updatedTable <- IO.blocking(updateAndSaveOwnReady(context.connection, table, targetSeat, command))
    yield updatedTable

  private def updateAndSaveOwnReady(
      connection: java.sql.Connection,
      table: Table,
      targetSeat: TableSeat,
      command: UpdateOwnReadyCommand
  ): Table =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
      connection,
      TableFunctions.updateSeatState(
        table,
        targetSeat = targetSeat.seat,
        ready = Some(command.ready),
        note = readyNote(command.actor, command.note)
      )
    )

  private def requireAuthenticatedPlayer(actor: AccessPrincipalPrivateView): PlayerId =
    actor.playerId.getOrElse(
      throw IllegalArgumentException("Only authenticated players can update their own ready state")
    )

  private def requirePlayerSeat(table: Table, tableId: TableId, playerId: PlayerId): TableSeat =
    table.seats.find(_.playerId == playerId).getOrElse(
      throw IllegalArgumentException(
        s"Player ${playerId.value} is not seated at table ${tableId.value}"
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

  private def readyNote(actor: AccessPrincipalPrivateView, note: Option[String]): Option[String] =
    note.map(message => s"${actor.displayName} updated their ready state: $message")

  private final case class UpdateOwnReadyCommand(
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      ready: Boolean,
      note: Option[String]
  )
