package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.tournament.mahjongcore.api.MahjongCoreStartTableAPIMessage
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.StartMahjongTableRequest
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TableFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      startedAt <- IO.realTimeInstant
      command = StartTableCommand(TableId(tableId), actor, startedAt)
      table <- IO.blocking {
        {
          startTable(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- CreateBulkNotificationsPrivateAPIMessage(tableStartedNotifications(context.connection, table)).plan(context)
    yield TournamentTableView.fromDomain(table)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipal(_).resolve(context.connection))
      .getOrElse(AccessPrincipalFunctions.system)

  private def startTable(connection: java.sql.Connection, command: StartTableCommand): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      )
      val startedTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, TableFunctions.start(table, command.startedAt))
      MahjongCoreStartTableAPIMessage.startAndSave(
        connection,
        command.tableId,
        StartMahjongTableRequest(
          operatorId = command.actor.playerId.map(_.value),
          ruleset = Some(rulesetForTable(connection, table))
        )
      )
      startedTable
    }

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
        notificationType = "TournamentTableStarted",
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

  private final case class StartTableCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      startedAt: Instant
  )
