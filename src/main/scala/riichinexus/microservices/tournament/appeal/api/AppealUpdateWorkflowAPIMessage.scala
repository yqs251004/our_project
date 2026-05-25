package riichinexus.microservices.tournament.appeal.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    request: UpdateAppealWorkflowRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO(context.support.principal(request.operator))
      updatedAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command <- IO(resolveCommand(actor, updatedAt))
      ticket <- IO(updateWorkflow(module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, updatedAt: Instant): UpdateAppealWorkflowCommand =
    UpdateAppealWorkflowCommand(
      ticketId = AppealTicketId(appealId),
      actor = actor,
      assigneeId = request.assignee,
      clearAssignee = request.clearAssignee,
      priority = request.priorityLevel,
      dueAt = request.dueAtInstant,
      clearDueAt = request.clearDueAt,
      note = request.note,
      updatedAt = updatedAt
    )

  private def updateWorkflow(
      module: TournamentAppealModuleContext,
      command: UpdateAppealWorkflowCommand
  ): AppealTicket =
    module.service.updateAppealWorkflow(
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

  private final case class UpdateAppealWorkflowCommand(
      ticketId: AppealTicketId,
      actor: AccessPrincipal,
      assigneeId: Option[PlayerId],
      clearAssignee: Boolean,
      priority: Option[AppealPriority],
      dueAt: Option[Instant],
      clearDueAt: Boolean,
      note: Option[String],
      updatedAt: Instant
  )
