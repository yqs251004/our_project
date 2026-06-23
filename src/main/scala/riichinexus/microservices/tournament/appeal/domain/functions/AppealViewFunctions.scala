package riichinexus.microservices.tournament.appeal.domain.functions

import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachment, AppealTicket}
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentView, AppealTicketView}

private[appeal] object AppealViewFunctions:
  def attachmentView(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = attachment.storageKind,
      mediaKind = attachment.mediaKind,
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt.map(_.toString)
    )

  def ticketView(ticket: AppealTicket): AppealTicketView =
    AppealTicketView(
      appealId = ticket.id.value,
      tableId = ticket.tableId.value,
      tournamentId = ticket.tournamentId.value,
      stageId = ticket.stageId.value,
      openedBy = ticket.openedBy.value,
      description = ticket.description,
      attachments = ticket.attachments.map(attachmentView),
      priority = ticket.priority,
      assigneeId = ticket.assigneeId.map(_.value),
      dueAt = ticket.dueAt.map(_.toString),
      status = ticket.status,
      logs = ticket.logs,
      reopenCount = ticket.reopenCount,
      createdAt = ticket.createdAt.toString,
      updatedAt = ticket.updatedAt.toString,
      resolution = ticket.resolution
    )
