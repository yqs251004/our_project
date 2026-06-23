package riichinexus.microservices.tournament.appeal.domain.functions

import java.sql.Connection
import java.util.NoSuchElementException

import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.appeal.domain.model.{AppealNotificationContext, AppealTicket}
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionType, AppealTableResolution}
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}

/** AppealNotificationRequestFunctions 提供申诉通知请求相关的领域计算、校验和转换函数。 */

private[appeal] object AppealNotificationRequestFunctions:
  def appealFiled(connection: Connection, ticket: AppealTicket): Vector[CreateNotificationRequest] =
    val context = loadContext(connection, ticket)
    context.tournament.admins.distinct.map { admin =>
      CreateNotificationRequest(
        recipientPlayerId = admin.value,
        notificationType = NotificationType.TournamentAppealFiled,
        title = "赛事申诉待处理",
        body = s"${context.tournament.name} / ${context.stageName} 的第 ${context.table.tableNo} 桌收到新的申诉，请及时处理。",
        severity = Some(NotificationSeverity.Warning),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.Appeal,
        sourceId = ticket.id.value,
        actionUrl = Some(s"/public/tournaments/${ticket.tournamentId.value}?tab=appeals"),
        objects = baseObjects(ticket, context) ++ Map(
          "openedBy" -> ticket.openedBy.value
        )
      )
    }

  def appealAdjudicated(
      connection: Connection,
      ticket: AppealTicket,
      decision: AppealDecisionType,
      tableResolution: Option[AppealTableResolution],
      verdict: String
  ): Vector[CreateNotificationRequest] =
    val context = loadContext(connection, ticket)
    val decisionText = decisionLabel(decision)
    Vector(
      CreateNotificationRequest(
        recipientPlayerId = ticket.openedBy.value,
        notificationType = NotificationType.TournamentAppealAdjudicated,
        title = s"赛事申诉$decisionText",
        body =
          s"${context.tournament.name} / ${context.stageName} 的第 ${context.table.tableNo} 桌申诉$decisionText。处理意见：${brief(verdict)}",
        severity = Some(decisionSeverity(decision)),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.Appeal,
        sourceId = ticket.id.value,
        actionUrl = Some("/me?tab=appeals"),
        objects = baseObjects(ticket, context) ++ Map(
          "decision" -> decision.toString,
          "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
        )
      )
    )

  private def loadContext(connection: Connection, ticket: AppealTicket): AppealNotificationContext =
    val tournament = TournamentTable
      .findById(connection, ticket.tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${ticket.tournamentId.value} was not found"))
    val table = TournamentGameTable
      .findById(connection, ticket.tableId)
      .getOrElse(throw NoSuchElementException(s"Table ${ticket.tableId.value} was not found"))
    val stageName = tournament.stages
      .find(_.id == ticket.stageId)
      .map(_.name)
      .getOrElse(ticket.stageId.value)

    AppealNotificationContext(tournament, table, stageName)

  private def baseObjects(ticket: AppealTicket, context: AppealNotificationContext): Map[String, String] =
    Map(
      "appealId" -> ticket.id.value,
      "tournamentId" -> ticket.tournamentId.value,
      "tournamentName" -> context.tournament.name,
      "stageId" -> ticket.stageId.value,
      "stageName" -> context.stageName,
      "tableId" -> ticket.tableId.value,
      "tableNo" -> context.table.tableNo.toString
    )

  private def decisionLabel(decision: AppealDecisionType): String =
    decision match
      case AppealDecisionType.Resolve  => "已解决"
      case AppealDecisionType.Reject   => "已驳回"
      case AppealDecisionType.Escalate => "已升级"

  private def decisionSeverity(decision: AppealDecisionType): NotificationSeverity =
    decision match
      case AppealDecisionType.Resolve  => NotificationSeverity.Success
      case AppealDecisionType.Reject   => NotificationSeverity.Info
      case AppealDecisionType.Escalate => NotificationSeverity.Warning

  private def brief(value: String): String =
    val trimmed = value.trim
    if trimmed.length <= 120 then trimmed else s"${trimmed.take(120)}..."
