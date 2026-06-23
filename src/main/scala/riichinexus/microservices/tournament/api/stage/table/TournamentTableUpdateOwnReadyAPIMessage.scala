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


import riichinexus.microservices.tournament.objects.stage.table.TableSeat
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{UpdateOwnTableReadyStateRequest}
import riichinexus.microservices.tournament.objects.stage.table.{TournamentTableView}
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** 更新当前玩家在牌桌上的准备状态。 */
final case class TournamentTableUpdateOwnReadyAPIMessage(tableId: String, request: UpdateOwnTableReadyStateRequest) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      requestedTableId = TableId(tableId)
      table <- updateOwnReady(context, requestedTableId, actor, request.ready, request.note).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tableView(table)

  private def updateOwnReady(
      context: ApiPlanContext,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      ready: Boolean,
      note: Option[String]
  ): IO[Option[Table]] =
    loadTable(context, tableId).flatMap {
      case Some(table) => updateLoadedTableOwnReady(context, table, tableId, actor, ready, note).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      TournamentGameTable.findById(context.connection, tableId)
    }

  private def updateLoadedTableOwnReady(
      context: ApiPlanContext,
      table: Table,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      ready: Boolean,
      note: Option[String]
  ): IO[Table] =
    val playerId = requireAuthenticatedPlayer(actor)
    val targetSeat = requirePlayerSeat(table, tableId, playerId)
    for
      _ <- requireSeatStatePermission(context, actor, table, targetSeat)
      updatedTable <- IO.blocking(updateAndSaveOwnReady(context.connection, table, targetSeat, actor, ready, note))
    yield updatedTable

  private def updateAndSaveOwnReady(
      connection: java.sql.Connection,
      table: Table,
      targetSeat: TableSeat,
      actor: AccessPrincipalPrivateView,
      ready: Boolean,
      note: Option[String]
  ): Table =
    TournamentGameTable.save(
      connection,
      TableFunctions.updateSeatState(
        table,
        targetSeat = targetSeat.seat,
        ready = Some(ready),
        note = readyNote(actor, note)
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

