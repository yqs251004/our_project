package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

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

final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    request: UpdateAppealWorkflowRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      updatedAt <- IO.realTimeInstant
      service = AppealApplicationService(AuthorizationPolicyFunctions.strict)
      command <- IO.blocking(resolveCommand(actor, updatedAt))
      ticket <- IO.blocking(updateWorkflow(context.connection, service, command))
      _ <- RecordAuditEventsPrivateAPIMessage(updateWorkflowAudit(ticket, command)).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, updatedAt: Instant): UpdateAppealWorkflowCommand =
    validateRequest()
    UpdateAppealWorkflowCommand(
      ticketId = AppealTicketId(appealId),
      actor = actor,
      assigneeId = request.assigneeId.map(PlayerId(_)),
      clearAssignee = request.clearAssignee,
      priority = request.priority.map(_.toDomain),
      dueAt = request.dueAt.map(Instant.parse),
      clearDueAt = request.clearDueAt,
      note = request.note,
      updatedAt = updatedAt
    )

  private def validateRequest(): Unit =
    require(
      !(request.clearAssignee && request.assigneeId.exists(_.trim.nonEmpty)),
      "clearAssignee cannot be combined with assigneeId"
    )
    require(
      !(request.clearDueAt && request.dueAt.exists(_.trim.nonEmpty)),
      "clearDueAt cannot be combined with dueAt"
    )

  private def updateWorkflow(
      connection: java.sql.Connection,
      service: AppealApplicationService,
      command: UpdateAppealWorkflowCommand
  ): AppealTicket =
    service.updateAppealWorkflow(
      connection = connection,
      ticketId = command.ticketId,
      actor = command.actor,
      assigneeId = command.assigneeId,
      clearAssignee = command.clearAssignee,
      priority = command.priority,
      dueAt = command.dueAt,
      clearDueAt = command.clearDueAt,
      updatedAt = command.updatedAt,
      note = command.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def updateWorkflowAudit(
      ticket: AppealTicket,
      command: UpdateAppealWorkflowCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = "AppealTicketWorkflowUpdated",
        occurredAt = command.updatedAt,
        actorId = command.actor.playerId,
        details = Map(
          "tournamentId" -> ticket.tournamentId.value,
          "tableId" -> ticket.tableId.value,
          "assigneeId" -> ticket.assigneeId.map(_.value).getOrElse("none"),
          "priority" -> ticket.priority.toString,
          "dueAt" -> ticket.dueAt.map(_.toString).getOrElse("none")
        ),
        note = command.note
      )
    )

  private final case class UpdateAppealWorkflowCommand(
      ticketId: AppealTicketId,
      actor: AccessPrincipal,
      assigneeId: Option[PlayerId],
      clearAssignee: Boolean,
      priority: Option[DomainAppealPriority],
      dueAt: Option[Instant],
      clearDueAt: Boolean,
      note: Option[String],
      updatedAt: Instant
  )
