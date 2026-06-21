package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.mahjongcore.api.`private`.{InitializeMahjongTableStatePrivateAPIMessage, InitializeMahjongTableStateRequest}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView

/** 开始赛事牌桌并初始化对局状态。 */
final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- resolveOperatorActor(context)
      startedAt <- IO.realTimeInstant
      command = StartTableCommand(TableId(tableId), actor, startedAt)
      table <- startTable(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      notificationRequests <- IO.blocking(tableStartedNotifications(context.connection, table))
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentViewFunctions.tableView(table)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def startTable(context: ApiPlanContext, command: StartTableCommand): IO[Option[Table]] =
    loadTable(context, command.tableId).flatMap {
      case Some(table) => startLoadedTable(context, table, command).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(context.connection, tableId)
    }

  private def startLoadedTable(
      context: ApiPlanContext,
      table: Table,
      command: StartTableCommand
  ): IO[Table] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      ).plan(context)
      startedTable <- IO.blocking(startAndSaveTable(context.connection, table, command))
    yield startedTable

  private def startAndSaveTable(
      connection: java.sql.Connection,
      table: Table,
      command: StartTableCommand
  ): Table =
    val startedTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
      connection,
      TableFunctions.start(table, command.startedAt)
    )
    InitializeMahjongTableStatePrivateAPIMessage.initializeAndSave(
      connection,
      command.tableId,
      InitializeMahjongTableStateRequest(
        operatorId = command.actor.playerId.map(_.value),
        ruleset = Some(rulesetForTable(connection, table))
      )
    )
    startedTable

  private def rulesetForTable(connection: java.sql.Connection, table: Table): MahjongRuleset =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable
      .findById(connection, table.tournamentId)
      .flatMap(_.stages.find(_.id == table.stageId))
      .map(_.mahjongRuleset)
      .getOrElse(MahjongRuleset())

  private def tableStartedNotifications(connection: java.sql.Connection, table: Table): Vector[CreateNotificationRequest] =
    val tournament = riichinexus.microservices.tournament.tables.tournaments.TournamentTable
      .findById(connection, table.tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${table.tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == table.stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${table.stageId.value} was not found"))

    table.seats.map { seat =>
      CreateNotificationRequest(
        recipientPlayerId = seat.playerId.value,
        notificationType = NotificationType.TournamentTableStarted,
        title = "\u8d5b\u4e8b\u724c\u684c\u5df2\u5f00\u59cb",
        body =
          s"${tournament.name} / ${stage.name} \u7684\u7b2c ${table.tableNo} \u684c\u5df2\u7ecf\u5f00\u59cb\uff0c\u8bf7\u8fdb\u5165\u724c\u684c\u5bf9\u5c40\u3002",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-table",
        sourceId = table.id.value,
        actionUrl = Some(s"/tables/${table.id.value}"),
        objects = Map(
          "tournamentId" -> tournament.id.value,
          "tournamentName" -> tournament.name,
          "stageId" -> stage.id.value,
          "stageName" -> stage.name,
          "tableId" -> table.id.value,
          "tableNo" -> table.tableNo.toString,
          "playerId" -> seat.playerId.value
        )
      )
    }

  /** 启动牌桌状态机时使用的已授权内部命令。 */
  private final case class StartTableCommand(
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      startedAt: Instant
  )
