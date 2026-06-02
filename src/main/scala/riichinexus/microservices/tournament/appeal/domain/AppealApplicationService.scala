package riichinexus.microservices.tournament.appeal.domain
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

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
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TableFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

final class AppealApplicationService(
    authorizationService: AuthorizationPolicy = AuthorizationPolicyFunctions.permitAll
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
    {
      TournamentGameTable.findById(connection, tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")
        AuthorizationPolicyFunctions.requirePermission(authorizationService, 
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
          id = AppealIdGenerator.appealTicketId(),
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

        val savedTicket = AppealTicketTable.save(connection, ticket)
        TournamentGameTable.save(connection, TableFunctions.flagAppeal(table, savedTicket.id, Some(description)))
        savedTicket
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
    {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        AuthorizationPolicyFunctions.requirePermission(authorizationService, 
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

        AppealTicketTable.save(connection, triagedTicket.copy(updatedAt = updatedAt))
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
    {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        AuthorizationPolicyFunctions.requirePermission(authorizationService, 
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

        val savedTicket = AppealTicketTable.save(connection, adjudicatedTicket)

        if decision != AppealDecisionType.Escalate then
          TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
            val updatedTable =
              tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                case AppealTableResolution.ForceReset =>
                  TableFunctions.forceReset(
                    table,
                    note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                    adjudicatedAt
                  )
                case resolution =>
                  TableFunctions.resolveAppeal(table, resolution, note)

            TournamentGameTable.save(connection, updatedTable)

            if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
              KnockoutStageCoordinator.reconcileAfterMatchMutation(
                connection,
                updatedTable.tournamentId,
                updatedTable.stageId,
                updatedTable.bracketMatchId.get,
                adjudicatedAt
              )
          }

        savedTicket
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
    {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        if actor.playerId.contains(ticket.openedBy) then ()
        else
          AuthorizationPolicyFunctions.requirePermission(authorizationService, 
            actor,
            Permission.ResolveAppeal,
            tournamentId = Some(ticket.tournamentId)
          )

        val reopenedTicket = AppealTicketTable.save(connection, ticket.reopen(operatorId, reason, reopenedAt, note))
        TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
          if table.status != TableStatus.Archived then
            TournamentGameTable.save(connection, TableFunctions.flagAppeal(table, ticket.id, note.orElse(Some(s"Appeal ${ticket.id.value} reopened"))))
        }
        reopenedTicket
      }
    }

  private def requireActiveAppealOperator(connection: Connection, playerId: PlayerId, context: String): Unit =
    val player = PlayerPersistenceFunctions.findPlayer(connection, playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
