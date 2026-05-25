package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: String,
    mediaKind: String,
    sizeBytes: Option[Long],
    uploadedAt: Option[String]
) derives CanEqual

object AppealAttachmentView:
  def fromDomain(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = attachment.storageKind.toString,
      mediaKind = attachment.mediaKind.toString,
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt.map(_.toString)
    )

final case class AppealDecisionLogView(
    operatorId: String,
    decision: String,
    decidedAt: String,
    note: Option[String]
) derives CanEqual

object AppealDecisionLogView:
  def fromDomain(log: AppealDecisionLog): AppealDecisionLogView =
    AppealDecisionLogView(
      operatorId = log.operatorId.value,
      decision = log.decision,
      decidedAt = log.decidedAt.toString,
      note = log.note
    )

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

type AppealTicketResponse = AppealTicketView

object TournamentAppealResponses:
  given ReadWriter[AppealAttachmentView] = macroRW
  given ReadWriter[AppealDecisionLogView] = macroRW
  given ReadWriter[AppealTicketView] = macroRW
