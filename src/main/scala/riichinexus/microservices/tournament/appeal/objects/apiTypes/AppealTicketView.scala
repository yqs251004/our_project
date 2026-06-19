package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionLog, AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AppealTicketView 表示申诉工单视图 的前端展示视图。 */

final case class AppealTicketView(
    appealId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    openedBy: String,
    description: String,
    attachments: Vector[AppealAttachmentView],
    priority: AppealPriority,
    assigneeId: Option[String],
    dueAt: Option[String],
    status: AppealStatus,
    logs: Vector[AppealDecisionLog],
    reopenCount: Int,
    createdAt: String,
    updatedAt: String,
    resolution: Option[String]
)

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
      priority = AppealPriority.fromDomain(ticket.priority),
      assigneeId = ticket.assigneeId.map(_.value),
      dueAt = ticket.dueAt.map(_.toString),
      status = AppealStatus.fromDomain(ticket.status),
      logs = ticket.logs,
      reopenCount = ticket.reopenCount,
      createdAt = ticket.createdAt.toString,
      updatedAt = ticket.updatedAt.toString,
      resolution = ticket.resolution
    )

  given ReadWriter[AppealTicketView] = macroRW
