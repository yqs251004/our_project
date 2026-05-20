package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    operatorId: String,
    assigneeId: Option[String] = None,
    clearAssignee: Boolean = false,
    priority: Option[String] = None,
    dueAt: Option[String] = None,
    clearDueAt: Boolean = false,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    IO {
      val request = UpdateAppealWorkflowRequest(operatorId, assigneeId, clearAssignee, priority, dueAt, clearDueAt, note)
      context.support.tournamentAppealModule.service.updateAppealWorkflow(
        ticketId = AppealTicketId(appealId),
        actor = context.support.principal(request.operator),
        assigneeId = request.assignee,
        clearAssignee = request.clearAssignee,
        priority = request.priorityLevel,
        dueAt = request.dueAtInstant,
        clearDueAt = request.clearDueAt,
        note = request.note
      ).map(AppealTicketView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
