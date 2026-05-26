package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealTicketView(
    appealId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    openedBy: String,
    description: String,
    attachments: Vector[AppealAttachmentView],
    priority: String,
    assigneeId: Option[String],
    dueAt: Option[String],
    status: String,
    logs: Vector[AppealDecisionLogView],
    reopenCount: Int,
    createdAt: String,
    updatedAt: String,
    resolution: Option[String]
) derives CanEqual

object AppealTicketView:
  def fromDomain(ticket: AppealTicket): AppealTicketView =
    AppealTicketView(
      appealId = ticket.id.value,
      tableId = ticket.tableId.value,
      tournamentId = ticket.tournamentId.value,
      stageId = ticket.stageId.value,
      openedBy = ticket.openedBy.value,
      description = ticket.description,
      attachments = ticket.attachments.map(AppealAttachmentView.fromDomain),
      priority = ticket.priority.toString,
      assigneeId = ticket.assigneeId.map(_.value),
      dueAt = ticket.dueAt.map(_.toString),
      status = ticket.status.toString,
      logs = ticket.logs.map(AppealDecisionLogView.fromDomain),
      reopenCount = ticket.reopenCount,
      createdAt = ticket.createdAt.toString,
      updatedAt = ticket.updatedAt.toString,
      resolution = ticket.resolution
    )

  given ReadWriter[AppealTicketView] = macroRW

