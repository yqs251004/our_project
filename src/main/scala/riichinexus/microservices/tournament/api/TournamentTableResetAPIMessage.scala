package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.mahjongcore.api.`private`.ResetMahjongTableStatePrivateAPIMessage
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.{ForceResetTableRequest, TournamentTableView}

import upickle.default.ReadWriter

/** 强制重置赛事牌桌状态。 */
final case class TournamentTableResetAPIMessage(tableId: String, request: ForceResetTableRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      resetAt <- IO.realTimeInstant
      command = ResetTableCommand(TableId(tableId), actor, request.note, resetAt)
      table <- resetTable(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- IO.blocking {
        ResetMahjongTableStatePrivateAPIMessage.resetAndSave(context.connection, command.tableId)
      }
    yield TournamentTableView.fromDomain(table)

  private def resetTable(context: ApiPlanContext, command: ResetTableCommand): IO[Option[Table]] =
    loadTable(context, command.tableId).flatMap {
      case Some(table) => resetLoadedTable(context, table, command).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(context.connection, tableId)
    }

  private def resetLoadedTable(
      context: ApiPlanContext,
      table: Table,
      command: ResetTableCommand
  ): IO[Table] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ResetTableState,
        tournamentId = Some(table.tournamentId)
      ).plan(context)
      resetTable <- IO.blocking(resetAndSaveTable(context.connection, table, command))
    yield resetTable

  private def resetAndSaveTable(
      connection: java.sql.Connection,
      table: Table,
      command: ResetTableCommand
  ): Table =
    deleteTableResultArtifacts(connection, table.id)
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
      connection,
      TableFunctions.forceReset(table, command.note, command.resetAt)
    )

  private def deleteTableResultArtifacts(connection: java.sql.Connection, tableId: TableId): Unit =
    MatchRecordTable.deleteByTable(connection, tableId)
    PaifuTable.deleteByTable(connection, tableId)

  private final case class ResetTableCommand(
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      note: String,
      resetAt: Instant
  )
