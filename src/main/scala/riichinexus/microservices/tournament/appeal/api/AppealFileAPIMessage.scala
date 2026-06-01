package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{
  AppealAttachment,
  AppealAttachmentMediaKind as DomainAppealAttachmentMediaKind,
  AppealAttachmentStorageKind as DomainAppealAttachmentStorageKind,
  AppealPriority as DomainAppealPriority,
  AppealTicket
}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealFileAPIMessage(
    tableId: String,
    request: FileAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.playerId)))
      createdAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command <- IO.blocking(resolveCommand(actor, createdAt))
      ticket <- IO.blocking(fileAppeal(context.connection, module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, createdAt: Instant): FileAppealCommand =
    FileAppealCommand(
      tableId = TableId(tableId),
      openedBy = PlayerId(request.playerId),
      description = request.description,
      attachments = request.attachments.map(appealAttachment),
      priority = request.priority.map(_.toDomain).getOrElse(DomainAppealPriority.Normal),
      dueAt = request.dueAt.map(Instant.parse),
      actor = actor,
      createdAt = createdAt
    )

  private def appealAttachment(request: AppealAttachmentRequest): AppealAttachment =
    AppealAttachment(
      name = request.name,
      uri = request.uri,
      contentType = request.contentType,
      storageKind = request.storageKind.map(_.toDomain).getOrElse(DomainAppealAttachmentStorageKind.ExternalUrl),
      mediaKind = request.mediaKind.map(_.toDomain).getOrElse(DomainAppealAttachmentMediaKind.Other),
      checksum = request.checksum,
      checksumAlgorithm = request.checksumAlgorithm,
      sizeBytes = request.sizeBytes,
      uploadedAt = request.uploadedAt,
      retentionUntil = request.retentionUntil
    )

  private def fileAppeal(
      connection: java.sql.Connection,
      module: TournamentAppealModuleContext,
      command: FileAppealCommand
  ): AppealTicket =
    module.service.fileAppeal(
      connection = connection,
      tableId = command.tableId,
      openedBy = command.openedBy,
      description = command.description,
      attachments = command.attachments,
      priority = command.priority,
      dueAt = command.dueAt,
      actor = command.actor,
      createdAt = command.createdAt
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class FileAppealCommand(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment],
      priority: DomainAppealPriority,
      dueAt: Option[Instant],
      actor: AccessPrincipal,
      createdAt: Instant
  )
