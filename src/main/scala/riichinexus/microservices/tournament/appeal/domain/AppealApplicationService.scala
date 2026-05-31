package riichinexus.microservices.tournament.appeal.domain

import riichinexus.microservices.tournament.objects.{TableStatus}

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.*
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

final class AppealApplicationService(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationPolicy = AuthorizationPolicy.permitAll
):
  def fileAppeal(
      connection: Connection,
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment] = Vector.empty,
      priority: AppealPriority = AppealPriority.Normal,
      dueAt: Option[Instant] = None,
      actor: AccessPrincipal,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      TournamentGameTable.findById(connection, tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")
        authorizationService.requirePermission(
          actor,
          Permission.FileAppealTicket,
          subjectPlayerId = Some(openedBy)
        )

        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")
        if table.status == TableStatus.Archived then
          throw IllegalArgumentException(s"Archived table ${tableId.value} cannot accept new appeals")
        if AppealTicketTable.findAll(connection).exists(ticket =>
            ticket.tableId == tableId &&
              (ticket.status == AppealStatus.Open ||
                ticket.status == AppealStatus.UnderReview ||
                ticket.status == AppealStatus.Escalated)
          )
        then
          throw IllegalArgumentException(
            s"Table ${tableId.value} already has an active appeal ticket"
          )

        val validatedAttachments = AppealAttachmentPolicy.validate(attachments, createdAt)
        val ticket = AppealTicket(
          id = IdGenerator.appealTicketId(),
          tableId = table.id,
          tournamentId = table.tournamentId,
          stageId = table.stageId,
          openedBy = openedBy,
          description = description,
          attachments = validatedAttachments,
          priority = priority,
          dueAt = dueAt,
          createdAt = createdAt,
          updatedAt = createdAt
        )

        DomainChangeInterpreter
          .auditAndEvents(transactionManager, auditEventRepository, eventBus)
          .commitWithinTransaction(
            DomainChange(
              aggregate = ticket,
              persist = nextTicket =>
                val savedTicket = AppealTicketTable.save(connection, nextTicket)
                TournamentGameTable.save(connection, table.flagAppeal(savedTicket.id, Some(description)))
                savedTicket,
              auditEntries = savedTicket =>
                Vector(
                  AuditEventEntry(
                    id = IdGenerator.auditEventId(),
                    aggregateType = "appeal",
                    aggregateId = savedTicket.id.value,
                    eventType = "AppealTicketFiled",
                    occurredAt = createdAt,
                    actorId = Some(openedBy),
                    details = Map(
                      "tableId" -> tableId.value,
                      "attachmentCount" -> savedTicket.attachments.size.toString,
                      "attachmentStorageKinds" -> savedTicket.attachments.map(_.storageKind.toString).distinct.sorted.mkString(","),
                      "attachmentMediaKinds" -> savedTicket.attachments.map(_.mediaKind.toString).distinct.sorted.mkString(",")
                    )
                  )
                ),
              domainEvents = savedTicket => Vector(AppealTicketFiled(savedTicket, createdAt))
            )
          )
      }
    }

  def updateAppealWorkflow(
      connection: Connection,
      ticketId: AppealTicketId,
      actor: AccessPrincipal,
      assigneeId: Option[PlayerId] = None,
      clearAssignee: Boolean = false,
      priority: Option[AppealPriority] = None,
      dueAt: Option[Instant] = None,
      clearDueAt: Boolean = false,
      updatedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        assigneeId.foreach(id => requireActiveAppealOperator(connection, id, "Appeal assignee must be an active player"))
        val nextAssignee =
          if clearAssignee then None
          else assigneeId.orElse(ticket.assigneeId)
        val nextPriority = priority.getOrElse(ticket.priority)
        val nextDueAt =
          if clearDueAt then None
          else dueAt.orElse(ticket.dueAt)

        require(nextDueAt.forall(!_.isBefore(updatedAt)), "Appeal dueAt cannot be earlier than workflow update time")

        val reassignedTicket =
          if nextAssignee != ticket.assigneeId then
            ticket.assign(operatorId, nextAssignee, updatedAt, note)
          else ticket

        val triagedTicket =
          if nextPriority != reassignedTicket.priority || nextDueAt != reassignedTicket.dueAt then
            reassignedTicket.reprioritize(operatorId, nextPriority, nextDueAt, updatedAt, note)
          else reassignedTicket

        DomainChangeInterpreter
          .auditAndEvents(transactionManager, auditEventRepository, eventBus)
          .commitWithinTransaction(
            DomainChange(
              aggregate = triagedTicket.copy(updatedAt = updatedAt),
              persist = nextTicket => AppealTicketTable.save(connection, nextTicket),
              auditEntries = savedTicket =>
                Vector(
                  AuditEventEntry(
                    id = IdGenerator.auditEventId(),
                    aggregateType = "appeal",
                    aggregateId = ticketId.value,
                    eventType = "AppealTicketWorkflowUpdated",
                    occurredAt = updatedAt,
                    actorId = actor.playerId,
                    details = Map(
                      "tournamentId" -> ticket.tournamentId.value,
                      "tableId" -> ticket.tableId.value,
                      "assigneeId" -> savedTicket.assigneeId.map(_.value).getOrElse("none"),
                      "priority" -> savedTicket.priority.toString,
                      "dueAt" -> savedTicket.dueAt.map(_.toString).getOrElse("none")
                    ),
                    note = note
                  )
                ),
              domainEvents = savedTicket => Vector(AppealTicketWorkflowUpdated(savedTicket, updatedAt))
            )
          )
      }
    }

  def resolveAppeal(
      connection: Connection,
      ticketId: AppealTicketId,
      verdict: String,
      actor: AccessPrincipal,
      resolvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    adjudicateAppeal(
      connection = connection,
      ticketId = ticketId,
      decision = AppealDecisionType.Resolve,
      verdict = verdict,
      actor = actor,
      adjudicatedAt = resolvedAt,
      tableResolution = Some(AppealTableResolution.RestorePriorState),
      note = note
    )

  def adjudicateAppeal(
      connection: Connection,
      ticketId: AppealTicketId,
      decision: AppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      adjudicatedAt: Instant = Instant.now(),
      tableResolution: Option[AppealTableResolution] = None,
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        val reviewedTicket =
          if ticket.status == AppealStatus.UnderReview then ticket
          else ticket.markUnderReview(operatorId, adjudicatedAt, note)

        val adjudicatedTicket =
          decision match
            case AppealDecisionType.Resolve =>
              reviewedTicket.resolve(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Reject =>
              reviewedTicket.reject(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Escalate =>
              reviewedTicket.escalate(operatorId, verdict, adjudicatedAt, note)

        DomainChangeInterpreter
          .auditAndEvents(transactionManager, auditEventRepository, eventBus)
          .commitWithinTransaction(
            DomainChange(
              aggregate = adjudicatedTicket,
              persist = nextTicket =>
                val savedTicket = AppealTicketTable.save(connection, nextTicket)

                if decision != AppealDecisionType.Escalate then
                  TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
                    val updatedTable =
                      tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                        case AppealTableResolution.ForceReset =>
                          table.forceReset(
                            note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                            adjudicatedAt
                          )
                        case resolution =>
                          table.resolveAppeal(resolution, note)

                    TournamentGameTable.save(connection, updatedTable)

                    if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
                      KnockoutStageCoordinator.reconcileAfterMatchMutation(
                        connection,
                        transactionManager,
                        updatedTable.tournamentId,
                        updatedTable.stageId,
                        updatedTable.bracketMatchId.get,
                        adjudicatedAt
                      )
                  }

                savedTicket,
              auditEntries = _ =>
                Vector(
                  AuditEventEntry(
                    id = IdGenerator.auditEventId(),
                    aggregateType = "appeal",
                    aggregateId = ticketId.value,
                    eventType = "AppealTicketAdjudicated",
                    occurredAt = adjudicatedAt,
                    actorId = actor.playerId,
                    details = Map(
                      "decision" -> decision.toString,
                      "tournamentId" -> ticket.tournamentId.value,
                      "tableId" -> ticket.tableId.value,
                      "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
                    ),
                    note = note.orElse(Some(verdict))
                  )
                ),
              domainEvents = savedTicket =>
                val resolvedEvents =
                  if decision == AppealDecisionType.Resolve then
                    Vector(AppealTicketResolved(savedTicket, adjudicatedAt))
                  else Vector.empty
                resolvedEvents :+ AppealTicketAdjudicated(
                  ticket = savedTicket,
                  decision = decision,
                  tableResolution =
                    if decision == AppealDecisionType.Escalate then None
                    else tableResolution.orElse(Some(AppealTableResolution.RestorePriorState)),
                  occurredAt = adjudicatedAt
                )
            )
          )
      }
    }

  def reopenAppeal(
      connection: Connection,
      ticketId: AppealTicketId,
      reason: String,
      actor: AccessPrincipal,
      reopenedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        if actor.playerId.contains(ticket.openedBy) then ()
        else
          authorizationService.requirePermission(
            actor,
            Permission.ResolveAppeal,
            tournamentId = Some(ticket.tournamentId)
          )

        DomainChangeInterpreter
          .auditAndEvents(transactionManager, auditEventRepository, eventBus)
          .commitWithinTransaction(
            DomainChange(
              aggregate = ticket.reopen(operatorId, reason, reopenedAt, note),
              persist = nextTicket =>
                val reopenedTicket = AppealTicketTable.save(connection, nextTicket)
                TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
                  if table.status != TableStatus.Archived then
                    TournamentGameTable.save(connection, table.flagAppeal(ticket.id, note.orElse(Some(s"Appeal ${ticket.id.value} reopened"))))
                }
                reopenedTicket,
              auditEntries = reopenedTicket =>
                Vector(
                  AuditEventEntry(
                    id = IdGenerator.auditEventId(),
                    aggregateType = "appeal",
                    aggregateId = ticketId.value,
                    eventType = "AppealTicketReopened",
                    occurredAt = reopenedAt,
                    actorId = actor.playerId,
                    details = Map(
                      "tournamentId" -> ticket.tournamentId.value,
                      "tableId" -> ticket.tableId.value,
                      "reopenCount" -> reopenedTicket.reopenCount.toString
                    ),
                    note = note.orElse(Some(reason))
                  )
                ),
              domainEvents = reopenedTicket => Vector(AppealTicketReopened(reopenedTicket, reopenedAt))
            )
          )
      }
    }

  private def requireActiveAppealOperator(connection: Connection, playerId: PlayerId, context: String): Unit =
    val player = PlayerTable.findById(connection, playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
