package riichinexus.microservices.tournament.api.stage.table
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType, StructuredEventField}
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.mahjongcore.api.gamestate.`private`.InitializeMahjongTableStatePrivateAPIMessage
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`.InitializeMahjongTableStateRequest
import riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions.MahjongRealtimeEventFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

import riichinexus.system.json.JsonCodecs.given
import riichinexus.system.realtime.objects.RealtimeSourceEventType
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView

/** 开始赛事牌桌并初始化对局状态。 */
final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- resolveOperatorActor(context)
      startedAt <- IO.realTimeInstant
      requestedTableId = TableId(tableId)
      startResult <- startTable(context, requestedTableId, actor, startedAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      table = startResult._1
      mahjongTable = startResult._2
      notificationRequests <- IO.blocking(tableStartedNotifications(context.connection, table))
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
      _ <- context.afterCommit(
        context.realtimeEventBus.publish(
          MahjongRealtimeEventFunctions.tableChanged(
            tableId = requestedTableId,
            sourceEventType = RealtimeSourceEventType.MahjongTableStarted,
            table = mahjongTable,
            actorId = actor.playerId,
            occurredAt = startedAt
          )
        )
      )
    yield TournamentViewFunctions.tableView(table)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def startTable(
      context: ApiPlanContext,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      startedAt: Instant
  ): IO[Option[(Table, MahjongTableView)]] =
    loadTable(context, tableId).flatMap {
      case Some(table) => startLoadedTable(context, table, tableId, actor, startedAt).map(Some(_))
      case None        => IO.pure(None)
    }

  private def loadTable(context: ApiPlanContext, tableId: TableId): IO[Option[Table]] =
    IO.blocking {
      TournamentGameTable.findById(context.connection, tableId)
    }

  private def startLoadedTable(
      context: ApiPlanContext,
      table: Table,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      startedAt: Instant
  ): IO[(Table, MahjongTableView)] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      ).plan(context)
      startedTable <- startAndSaveTable(context, table, tableId, actor, startedAt)
    yield startedTable

  private def startAndSaveTable(
      context: ApiPlanContext,
      table: Table,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      startedAt: Instant
  ): IO[(Table, MahjongTableView)] =
    for
      ruleset <- IO.blocking(rulesetForTable(context.connection, table))
      startedTable <- IO.blocking(
        TournamentGameTable.save(
          context.connection,
          TableFunctions.start(table, startedAt)
        )
      )
      mahjongTable <- InitializeMahjongTableStatePrivateAPIMessage(
        tableId.value,
        InitializeMahjongTableStateRequest(
          operatorId = actor.playerId.map(_.value),
          ruleset = Some(ruleset)
        )
      ).plan(context)
    yield startedTable -> mahjongTable

  private def rulesetForTable(connection: java.sql.Connection, table: Table): MahjongRuleset =
    TournamentTable
      .findById(connection, table.tournamentId)
      .flatMap(_.stages.find(_.id == table.stageId))
      .map(_.mahjongRuleset)
      .getOrElse(MahjongRuleset())

  private def tableStartedNotifications(connection: java.sql.Connection, table: Table): Vector[CreateNotificationRequest] =
    val tournament = TournamentTable
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
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.TournamentTable,
        sourceId = table.id.value,
        actionUrl = Some(s"/tables/${table.id.value}"),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> tournament.id.value,
          StructuredEventField.toString(StructuredEventField.TournamentName) -> tournament.name,
          StructuredEventField.toString(StructuredEventField.StageId) -> stage.id.value,
          StructuredEventField.toString(StructuredEventField.StageName) -> stage.name,
          StructuredEventField.toString(StructuredEventField.TableId) -> table.id.value,
          StructuredEventField.toString(StructuredEventField.TableNo) -> table.tableNo.toString,
          StructuredEventField.toString(StructuredEventField.PlayerId) -> seat.playerId.value
        )
      )
    }
