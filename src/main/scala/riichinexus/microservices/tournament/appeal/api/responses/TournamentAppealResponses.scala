package riichinexus.microservices.tournament.appeal.api.responses

import java.time.Instant

import riichinexus.domain.model.*

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: AppealAttachmentStorageKind,
    mediaKind: AppealAttachmentMediaKind,
    sizeBytes: Option[Long],
    uploadedAt: Option[Instant]
) derives CanEqual

object AppealAttachmentView:
  def fromDomain(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = attachment.storageKind,
      mediaKind = attachment.mediaKind,
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt
    )

final case class AppealDecisionLogView(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String]
) derives CanEqual

object AppealDecisionLogView:
  def fromDomain(log: AppealDecisionLog): AppealDecisionLogView =
    AppealDecisionLogView(
      operatorId = log.operatorId,
      decision = log.decision,
      decidedAt = log.decidedAt,
      note = log.note
    )

final case class AppealTicketView(
    appealId: AppealTicketId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    openedBy: PlayerId,
    description: String,
    attachments: Vector[AppealAttachmentView],
    priority: AppealPriority,
    assigneeId: Option[PlayerId],
    dueAt: Option[Instant],
    status: AppealStatus,
    logs: Vector[AppealDecisionLogView],
    reopenCount: Int,
    createdAt: Instant,
    updatedAt: Instant,
    resolution: Option[String]
) derives CanEqual

object AppealTicketView:
  def fromDomain(ticket: AppealTicket): AppealTicketView =
    AppealTicketView(
      appealId = ticket.id,
      tableId = ticket.tableId,
      tournamentId = ticket.tournamentId,
      stageId = ticket.stageId,
      openedBy = ticket.openedBy,
      description = ticket.description,
      attachments = ticket.attachments.map(AppealAttachmentView.fromDomain),
      priority = ticket.priority,
      assigneeId = ticket.assigneeId,
      dueAt = ticket.dueAt,
      status = ticket.status,
      logs = ticket.logs.map(AppealDecisionLogView.fromDomain),
      reopenCount = ticket.reopenCount,
      createdAt = ticket.createdAt,
      updatedAt = ticket.updatedAt,
      resolution = ticket.resolution
    )

type AppealTicketResponse = AppealTicketView

object TournamentAppealResponses:
  export TournamentAppealResponseCodecs.given
