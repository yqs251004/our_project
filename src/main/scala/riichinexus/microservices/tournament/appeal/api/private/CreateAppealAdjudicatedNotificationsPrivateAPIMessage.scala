package riichinexus.microservices.tournament.appeal.api.`private`

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.tournament.appeal.domain.model.{AppealDecisionType, AppealTableResolution, AppealTicket}
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreateAppealAdjudicatedNotificationsPrivateAPIMessage(
    ticket: AppealTicket,
    decision: AppealDecisionType,
    tableResolution: Option[AppealTableResolution],
    verdict: String
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    for
      requests <- IO.blocking(notificationRequests(context.connection))
      notifications <- CreateBulkNotificationsPrivateAPIMessage(requests).plan(context)
    yield notifications

  private def notificationRequests(connection: Connection): Vector[CreateNotificationRequest] =
    val context = loadContext(connection)
    val decisionText = decisionLabel(decision)
    Vector(
      CreateNotificationRequest(
        recipientPlayerId = ticket.openedBy.value,
        notificationType = "TournamentAppealAdjudicated",
        title = s"赛事申诉$decisionText",
        body =
          s"${context.tournament.name} / ${context.stageName} 的第 ${context.table.tableNo} 桌申诉$decisionText。处理意见：${brief(verdict)}",
        severity = Some(decisionSeverity(decision)),
        sourceService = "tournament",
        sourceType = "appeal",
        sourceId = ticket.id.value,
        actionUrl = Some("/me?tab=appeals"),
        objects = baseObjects(context) ++ Map(
          "decision" -> decision.toString,
          "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
        )
      )
    )

  private def loadContext(connection: Connection): AppealNotificationContext =
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

  private def baseObjects(context: AppealNotificationContext): Map[String, String] =
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

  private def decisionSeverity(decision: AppealDecisionType): String =
    decision match
      case AppealDecisionType.Resolve  => "success"
      case AppealDecisionType.Reject   => "info"
      case AppealDecisionType.Escalate => "warning"

  private def brief(value: String): String =
    val trimmed = value.trim
    if trimmed.length <= 120 then trimmed else s"${trimmed.take(120)}..."

  private final case class AppealNotificationContext(
      tournament: Tournament,
      table: Table,
      stageName: String
  )
