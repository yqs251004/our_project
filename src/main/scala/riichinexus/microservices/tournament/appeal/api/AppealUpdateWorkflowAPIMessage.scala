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

final case class AppealUpdateWorkflowAPIMessage(
    appealId: String,
    request: UpdateAppealWorkflowRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.operatorId)))
      updatedAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command <- IO.blocking(resolveCommand(actor, updatedAt))
      ticket <- IO.blocking(updateWorkflow(context.connection, module, command))
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
      module: TournamentAppealModuleContext,
      command: UpdateAppealWorkflowCommand
  ): AppealTicket =
    module.service.updateAppealWorkflow(
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
