package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealNotificationRequestFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachment, AppealAttachmentMediaKind as DomainAppealAttachmentMediaKind, AppealAttachmentStorageKind as DomainAppealAttachmentStorageKind, AppealPriority as DomainAppealPriority, AppealTicket}

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AppealAttachmentRequest, AppealTicketView, FileAppealRequest}
import upickle.default.ReadWriter

/** 提交牌桌申诉工单。 */
final case class AppealFileAPIMessage(
    tableId: String,
    request: FileAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.playerId)).plan(context)
      createdAt <- IO.realTimeInstant
      command <- IO.delay(resolveCommand(actor, createdAt))
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.FileAppealTicket,
        subjectPlayerId = Some(command.openedBy)
      ).plan(context)
      ticket <- IO.blocking(fileAppeal(context.connection, command))
      _ <- RecordAuditEventsPrivateAPIMessage(fileAppealAudit(ticket, command)).plan(context)
      notifications <- IO.blocking(AppealNotificationRequestFunctions.appealFiled(context.connection, ticket))
      _ <- RecordBulkNotificationsPrivateAPIMessage(notifications).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipalPrivateView, createdAt: Instant): FileAppealCommand =
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
      command: FileAppealCommand
  ): AppealTicket =
    AppealApplicationService.fileAppeal(
      connection = connection,
      tableId = command.tableId,
      openedBy = command.openedBy,
      description = command.description,
      attachments = command.attachments,
      priority = command.priority,
      dueAt = command.dueAt,
      actor = privateActor(command.actor),
      createdAt = command.createdAt
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def privateActor(actor: AccessPrincipalPrivateView): AccessPrincipalPrivateView =
    actor

  private def fileAppealAudit(
      ticket: AppealTicket,
      command: FileAppealCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "appeal",
        aggregateId = ticket.id.value,
        eventType = "AppealTicketFiled",
        occurredAt = command.createdAt,
        actorId = Some(command.openedBy),
        details = Map(
          "tableId" -> command.tableId.value,
          "attachmentCount" -> ticket.attachments.size.toString,
          "attachmentStorageKinds" -> ticket.attachments.map(_.storageKind.toString).distinct.sorted.mkString(","),
          "attachmentMediaKinds" -> ticket.attachments.map(_.mediaKind.toString).distinct.sorted.mkString(",")
        )
      )
    )

  private final case class FileAppealCommand(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment],
      priority: DomainAppealPriority,
      dueAt: Option[Instant],
      actor: AccessPrincipalPrivateView,
      createdAt: Instant
  )
