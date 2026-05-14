package riichinexus.microservices.tournament.appeal.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.api.requests.*

object AppealWorkflowApi:

  def fileAppeal(
      service: AppealApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      request: FileAppealRequest
  ): Option[AppealTicket] =
    service.fileAppeal(
      tableId = tableId,
      openedBy = request.player,
      description = request.description,
      attachments = request.attachments.map(_.toAttachment),
      priority = request.priorityLevel,
      dueAt = request.dueAtInstant,
      actor = principalOf(request.player)
    )

  def resolveAppeal(
      service: AppealApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      ticketId: AppealTicketId,
      request: ResolveAppealRequest
  ): Option[AppealTicket] =
    service.resolveAppeal(
      ticketId = ticketId,
      verdict = request.verdict,
      actor = principalOf(request.operator),
      note = request.note
    )

  def adjudicateAppeal(
      service: AppealApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      ticketId: AppealTicketId,
      request: AdjudicateAppealRequest
  ): Option[AppealTicket] =
    service.adjudicateAppeal(
      ticketId = ticketId,
      decision = request.decisionType,
      verdict = request.verdict,
      actor = principalOf(request.operator),
      tableResolution = request.resolution,
      note = request.note
    )

  def updateWorkflow(
      service: AppealApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      ticketId: AppealTicketId,
      request: UpdateAppealWorkflowRequest
  ): Option[AppealTicket] =
    service.updateAppealWorkflow(
      ticketId = ticketId,
      actor = principalOf(request.operator),
      assigneeId = request.assignee,
      clearAssignee = request.clearAssignee,
      priority = request.priorityLevel,
      dueAt = request.dueAtInstant,
      clearDueAt = request.clearDueAt,
      note = request.note
    )

  def reopenAppeal(
      service: AppealApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      ticketId: AppealTicketId,
      request: ReopenAppealRequest
  ): Option[AppealTicket] =
    service.reopenAppeal(
      ticketId = ticketId,
      reason = request.reason,
      actor = principalOf(request.operator),
      note = request.note
    )
