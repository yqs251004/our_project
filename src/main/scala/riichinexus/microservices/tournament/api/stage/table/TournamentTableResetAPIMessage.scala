package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.mahjongcore.api.gamestate.`private`.ResetMahjongTableStatePrivateAPIMessage
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`.ResetMahjongTableStateRequest
import riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions.MahjongRealtimeEventFunctions
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{ForceResetTableRequest}
import riichinexus.microservices.tournament.objects.stage.table.{TournamentTableView}
import riichinexus.system.realtime.objects.RealtimeSourceEventType

/** 强制重置赛事牌桌状态。 */
final case class TournamentTableResetAPIMessage(tableId: String, request: ForceResetTableRequest) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      resetAt <- IO.realTimeInstant
      requestedTableId = TableId(tableId)
      table <- resetTable(context, requestedTableId, actor, request.note, resetAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      mahjongTable <- ResetMahjongTableStatePrivateAPIMessage(
        requestedTableId.value,
        ResetMahjongTableStateRequest(actor.playerId.map(_.value), request.note)
      ).plan(context)
      _ <- context.afterCommit(
        context.realtimeEventBus.publish(
          MahjongRealtimeEventFunctions.tableChanged(
            tableId = requestedTableId,
            sourceEventType = RealtimeSourceEventType.MahjongTableReset,
            table = mahjongTable,
            actorId = actor.playerId,
            occurredAt = resetAt
          )
        )
      )
    yield TournamentViewFunctions.tableView(table)

  private def resetTable(
      context: ApiPlanContext,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      note: String,
      resetAt: Instant
  ): IO[Option[Table]] =
    loadTable(context, tableId).flatMap {
      case Some(table) => resetLoadedTable(context, table, actor, note, resetAt).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      TournamentGameTable.findById(context.connection, tableId)
    }

  private def resetLoadedTable(
      context: ApiPlanContext,
      table: Table,
      actor: AccessPrincipalPrivateView,
      note: String,
      resetAt: Instant
  ): IO[Table] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ResetTableState,
        tournamentId = Some(table.tournamentId)
      ).plan(context)
      resetTable <- IO.blocking(resetAndSaveTable(context.connection, table, note, resetAt))
    yield resetTable

  private def resetAndSaveTable(
      connection: java.sql.Connection,
      table: Table,
      note: String,
      resetAt: Instant
  ): Table =
    deleteTableResultArtifacts(connection, table.id)
    TournamentGameTable.save(
      connection,
      TableFunctions.forceReset(table, note, resetAt)
    )

  private def deleteTableResultArtifacts(connection: java.sql.Connection, tableId: TableId): Unit =
    MatchRecordTable.deleteByTable(connection, tableId)
    PaifuTable.deleteByTable(connection, tableId)

