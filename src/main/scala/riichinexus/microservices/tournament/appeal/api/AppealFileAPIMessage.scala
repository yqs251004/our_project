package riichinexus.microservices.tournament.appeal.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealNotificationRequestFunctions
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachment, AppealTicket}
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind, AppealPriority}

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AppealAttachmentRequest, FileAppealRequest}
import riichinexus.microservices.tournament.appeal.objects.{AppealTicketView}
/** 提交牌桌申诉工单。 */
final case class AppealFileAPIMessage(
    tableId: String,
    request: FileAppealRequest
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.playerId)).plan(context)
      createdAt <- IO.realTimeInstant
      requestedTableId = TableId(tableId)
      openedBy = PlayerId(request.playerId)
      attachments = request.attachments.map(appealAttachment)
      priority = request.priority.getOrElse(AppealPriority.Normal)
      dueAt = request.dueAt.map(Instant.parse)
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.FileAppealTicket,
        subjectPlayerId = Some(openedBy)
      ).plan(context)
      ticket <- IO.blocking(fileAppeal(context.connection, requestedTableId, openedBy, attachments, priority, dueAt, actor, createdAt))
      _ <- RecordAuditEventsPrivateAPIMessage(fileAppealAudit(ticket, requestedTableId, openedBy, createdAt)).plan(context)
      notifications <- IO.blocking(AppealNotificationRequestFunctions.appealFiled(context.connection, ticket))
      _ <- RecordBulkNotificationsPrivateAPIMessage(notifications).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def appealAttachment(request: AppealAttachmentRequest): AppealAttachment =
    AppealAttachment(
      name = request.name,
      uri = request.uri,
      contentType = request.contentType,
      storageKind = request.storageKind.getOrElse(AppealAttachmentStorageKind.ExternalUrl),
      mediaKind = request.mediaKind.getOrElse(AppealAttachmentMediaKind.Other),
      checksum = request.checksum,
      checksumAlgorithm = request.checksumAlgorithm,
      sizeBytes = request.sizeBytes,
      uploadedAt = request.uploadedAt,
      retentionUntil = request.retentionUntil
    )

  private def fileAppeal(
      connection: java.sql.Connection,
      tableId: TableId,
      openedBy: PlayerId,
      attachments: Vector[AppealAttachment],
      priority: AppealPriority,
      dueAt: Option[Instant],
      actor: AccessPrincipalPrivateView,
      createdAt: Instant
  ): AppealTicket =
    AppealApplicationService.fileAppeal(
      connection = connection,
      tableId = tableId,
      openedBy = openedBy,
      description = request.description,
      attachments = attachments,
      priority = priority,
      dueAt = dueAt,
      actor = actor,
      createdAt = createdAt
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def fileAppealAudit(
      ticket: AppealTicket,
      tableId: TableId,
      openedBy: PlayerId,
      createdAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Appeal,
        aggregateId = ticket.id.value,
        eventType = AuditEventType.AppealTicketFiled,
        occurredAt = createdAt,
        actorId = Some(openedBy),
        details = Map(
          StructuredEventField.toString(StructuredEventField.TableId) -> tableId.value,
          StructuredEventField.toString(StructuredEventField.AttachmentCount) -> ticket.attachments.size.toString,
          StructuredEventField.toString(StructuredEventField.AttachmentStorageKinds) -> ticket.attachments.map(_.storageKind.toString).distinct.sorted.mkString(","),
          StructuredEventField.toString(StructuredEventField.AttachmentMediaKinds) -> ticket.attachments.map(_.mediaKind.toString).distinct.sorted.mkString(",")
        )
      )
    )
