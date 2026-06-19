package riichinexus.microservices.tournament.appeal.domain.functions

import riichinexus.microservices.tournament.objects.stage.table.TableStatus

import java.sql.Connection
import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachment, AppealDecisionType, AppealPriority, AppealStatus, AppealTableResolution, AppealTicket}

import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** AppealApplicationService 提供申诉申请服务 相关的领域计算、校验和转换函数。 */

private[appeal] object AppealApplicationService:
  def fileAppeal(
      connection: Connection,
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment] = Vector.empty,
      priority: AppealPriority = AppealPriority.Normal,
      dueAt: Option[Instant] = None,
      actor: AccessPrincipalPrivateView,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    {
      TournamentGameTable.findById(connection, tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")
        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")
        if table.status != TableStatus.Scoring then
          throw IllegalArgumentException(s"Only scoring table ${tableId.value} can accept new appeals")
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
      actor: AccessPrincipalPrivateView,
      assigneeId: Option[PlayerId] = None,
      clearAssignee: Boolean = false,
      priority: Option[AppealPriority] = None,
      dueAt: Option[Instant] = None,
      clearDueAt: Boolean = false,
      updatedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): IO[Option[AppealTicket]] =
    IO.blocking(AppealTicketTable.findById(connection, ticketId)).flatMap {
      case Some(ticket) =>
        for
          saved <- IO.blocking {
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
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
        yield Some(saved)
      case None => IO.pure(None)
    }

  def resolveAppeal(
      connection: Connection,
      ticketId: AppealTicketId,
      verdict: String,
      actor: AccessPrincipalPrivateView,
      resolvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): IO[Option[AppealTicket]] =
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
      actor: AccessPrincipalPrivateView,
      adjudicatedAt: Instant = Instant.now(),
      tableResolution: Option[AppealTableResolution] = None,
      note: Option[String] = None
  ): IO[Option[AppealTicket]] =
    IO.blocking {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
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
        val reconcileInputs =
          collection.mutable.ArrayBuffer.empty[(TournamentId, TournamentStageId, String)]

        if decision != AppealDecisionType.Escalate then
          TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
            val updatedTable =
              tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                case AppealTableResolution.ForceReset =>
                  deleteTableResultArtifacts(connection, table.id)
                  TableFunctions.forceReset(
                    table,
                    note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                    adjudicatedAt
                  )
                case resolution =>
                  TableFunctions.resolveAppeal(table, resolution, note)

            TournamentGameTable.save(connection, updatedTable)

            if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
              reconcileInputs += ((updatedTable.tournamentId, updatedTable.stageId, updatedTable.bracketMatchId.get))
          }

        (savedTicket, reconcileInputs.toVector)
      }
    }.flatMap {
      case Some((savedTicket, reconcileInputs)) =>
        runReconciliations(connection, reconcileInputs, adjudicatedAt).as(Some(savedTicket))
      case None => IO.pure(None)
    }

  def reopenAppeal(
      connection: Connection,
      ticketId: AppealTicketId,
      reason: String,
      actor: AccessPrincipalPrivateView,
      reopenedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    {
      AppealTicketTable.findById(connection, ticketId).map { ticket =>
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)

        val reopenedTicket = AppealTicketTable.save(connection, ticket.reopen(operatorId, reason, reopenedAt, note))
        TournamentGameTable.findById(connection, ticket.tableId).foreach { table =>
          if table.status == TableStatus.Scoring then
            TournamentGameTable.save(connection, TableFunctions.flagAppeal(table, ticket.id, note.orElse(Some(s"Appeal ${ticket.id.value} reopened"))))
        }
        reopenedTicket
      }
    }

  private def runReconciliations(
      connection: Connection,
      reconcileInputs: Vector[(TournamentId, TournamentStageId, String)],
      at: Instant
  ): IO[Unit] =
    reconcileInputs.foldLeft(IO.unit) { case (effect, (tournamentId, stageId, matchId)) =>
      effect.flatMap(_ =>
        KnockoutStageCoordinator.reconcileAfterMatchMutation(
          connection,
          tournamentId,
          stageId,
          matchId,
          at
        ).map(_ => ())
      )
    }

  private def deleteTableResultArtifacts(connection: Connection, tableId: TableId): Unit =
    MatchRecordTable.deleteByTable(connection, tableId)
    PaifuTable.deleteByTable(connection, tableId)
