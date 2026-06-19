package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.api.`private`.ResolvePlayerReadModelsPrivateAPIMessage
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.AppealPriority

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AppealTicketView, UpdateAppealWorkflowRequest}
/** 更新申诉工单的分派、优先级或截止时间。 */
final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    request: UpdateAppealWorkflowRequest
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      updatedAt <- IO.realTimeInstant
      command <- IO.delay(resolveCommand(actor, updatedAt))
      existingTicket <- IO.blocking(
        AppealTicketTable.findById(context.connection, command.ticketId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ResolveAppeal,
        tournamentId = Some(existingTicket.tournamentId)
      ).plan(context)
      _ <- requireActiveAssignee(context, command.assigneeId)
      ticket <- updateWorkflow(context.connection, command)
      _ <- RecordAuditEventsPrivateAPIMessage(updateWorkflowAudit(ticket, command)).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def resolveCommand(actor: AccessPrincipalPrivateView, updatedAt: Instant): UpdateAppealWorkflowCommand =
    validateRequest()
    UpdateAppealWorkflowCommand(
      ticketId = AppealTicketId(appealId),
      actor = actor,
      assigneeId = request.assigneeId.map(PlayerId(_)),
      clearAssignee = request.clearAssignee,
      priority = request.priority,
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
      command: UpdateAppealWorkflowCommand
  ): IO[AppealTicket] =
    AppealApplicationService.updateAppealWorkflow(
      connection = connection,
      ticketId = command.ticketId,
      actor = privateActor(command.actor),
      assigneeId = command.assigneeId,
      clearAssignee = command.clearAssignee,
      priority = command.priority,
      dueAt = command.dueAt,
      clearDueAt = command.clearDueAt,
      updatedAt = command.updatedAt,
      note = command.note
    ).map(_.getOrElse(throw NoSuchElementException("Resource not found")))

  private def requireActiveAssignee(context: ApiPlanContext, assigneeId: Option[PlayerId]): IO[Unit] =
    assigneeId
      .map { id =>
        ResolvePlayerReadModelsPrivateAPIMessage(Vector(id))
          .plan(context)
          .map(_.headOption.getOrElse(throw NoSuchElementException(s"Player ${id.value} was not found")))
          .flatMap { player =>
            if !player.active then
              IO.raiseError(IllegalArgumentException("Appeal assignee must be an active player"))
            else IO.unit
          }
      }
      .getOrElse(IO.unit)

  private def privateActor(actor: AccessPrincipalPrivateView): AccessPrincipalPrivateView =
    actor

  private def updateWorkflowAudit(
      ticket: AppealTicket,
      command: UpdateAppealWorkflowCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = AuditEventType.AppealTicketWorkflowUpdated,
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
      actor: AccessPrincipalPrivateView,
      assigneeId: Option[PlayerId],
      clearAssignee: Boolean,
      priority: Option[AppealPriority],
      dueAt: Option[Instant],
      clearDueAt: Boolean,
      note: Option[String],
      updatedAt: Instant
  )
