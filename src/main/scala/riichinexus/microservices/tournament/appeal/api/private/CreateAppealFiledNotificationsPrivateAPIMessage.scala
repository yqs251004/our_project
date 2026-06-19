package riichinexus.microservices.tournament.appeal.api.`private`

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreateAppealFiledNotificationsPrivateAPIMessage(
    ticket: AppealTicket
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    for
      requests <- IO.blocking(notificationRequests(context.connection))
      notifications <- CreateBulkNotificationsPrivateAPIMessage(requests).plan(context)
    yield notifications

  private def notificationRequests(connection: Connection): Vector[CreateNotificationRequest] =
    val context = loadContext(connection)
    context.tournament.admins.distinct.map { admin =>
      CreateNotificationRequest(
        recipientPlayerId = admin.value,
        notificationType = "TournamentAppealFiled",
        title = "赛事申诉待处理",
        body = s"${context.tournament.name} / ${context.stageName} 的第 ${context.table.tableNo} 桌收到新的申诉，请及时处理。",
        severity = Some("warning"),
        sourceService = "tournament",
        sourceType = "appeal",
        sourceId = ticket.id.value,
        actionUrl = Some(s"/public/tournaments/${ticket.tournamentId.value}?tab=appeals"),
        objects = baseObjects(context) ++ Map(
          "openedBy" -> ticket.openedBy.value
        )
      )
    }

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

  private final case class AppealNotificationContext(
      tournament: Tournament,
      table: Table,
      stageName: String
  )
