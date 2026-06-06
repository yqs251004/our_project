package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealFileAPIMessage(
    tableId: String,
    request: FileAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.playerId)).resolve(context.connection))
      createdAt <- IO.realTimeInstant
      service = AppealApplicationService(AuthorizationPolicyFunctions.strict)
      command <- IO.blocking(resolveCommand(actor, createdAt))
      ticket <- IO.blocking(fileAppeal(context.connection, service, command))
      _ <- RecordAuditEventsPrivateAPIMessage(fileAppealAudit(ticket, command)).plan(context)
      _ <- CreateBulkNotificationsPrivateAPIMessage(
        AppealNotificationRequests.appealFiled(context.connection, ticket)
      ).plan(context)
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
      service: AppealApplicationService,
      command: FileAppealCommand
  ): AppealTicket =
    service.fileAppeal(
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

  private def fileAppealAudit(
      ticket: AppealTicket,
      command: FileAppealCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
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
      actor: AccessPrincipal,
      createdAt: Instant
  )
