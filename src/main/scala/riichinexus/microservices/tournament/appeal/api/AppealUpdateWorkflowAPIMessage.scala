package riichinexus.microservices.tournament.appeal.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.api.`private`.ResolvePlayerReadModelsPrivateAPIMessage
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.appeal.objects.AppealTicketId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.AppealPriority

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{UpdateAppealWorkflowRequest}
import riichinexus.microservices.tournament.appeal.objects.{AppealTicketView}
/** 更新申诉工单的分派、优先级或截止时间。 */
final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    request: UpdateAppealWorkflowRequest
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      updatedAt <- IO.realTimeInstant
      requestedAppealId <- IO.delay(resolveInput())
      assigneeId = request.assigneeId.map(PlayerId(_))
      dueAt = request.dueAt.map(Instant.parse)
      existingTicket <- IO.blocking(
        AppealTicketTable.findById(context.connection, requestedAppealId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ResolveAppeal,
        tournamentId = Some(existingTicket.tournamentId)
      ).plan(context)
      _ <- requireActiveAssignee(context, assigneeId)
      ticket <- updateWorkflow(context.connection, requestedAppealId, actor, assigneeId, dueAt, updatedAt)
      _ <- RecordAuditEventsPrivateAPIMessage(updateWorkflowAudit(ticket, requestedAppealId, actor, updatedAt)).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def resolveInput(): AppealTicketId =
    validateRequest()
    AppealTicketId(appealId)

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
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      assigneeId: Option[PlayerId],
      dueAt: Option[Instant],
      updatedAt: Instant
  ): IO[AppealTicket] =
    AppealApplicationService.updateAppealWorkflow(
      connection = connection,
      ticketId = ticketId,
      actor = actor,
      assigneeId = assigneeId,
      clearAssignee = request.clearAssignee,
      priority = request.priority,
      dueAt = dueAt,
      clearDueAt = request.clearDueAt,
      updatedAt = updatedAt,
      note = request.note
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

  private def updateWorkflowAudit(
      ticket: AppealTicket,
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      updatedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Appeal,
        aggregateId = ticketId.value,
        eventType = AuditEventType.AppealTicketWorkflowUpdated,
        occurredAt = updatedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> ticket.tournamentId.value,
          StructuredEventField.toString(StructuredEventField.TableId) -> ticket.tableId.value,
          StructuredEventField.toString(StructuredEventField.AssigneeId) -> ticket.assigneeId.map(_.value).getOrElse("none"),
          StructuredEventField.toString(StructuredEventField.Priority) -> ticket.priority.toString,
          StructuredEventField.toString(StructuredEventField.DueAt) -> ticket.dueAt.map(_.toString).getOrElse("none")
        ),
        note = request.note
      )
    )
