package riichinexus.microservices.tournament.appeal.tables

import riichinexus.application.ports.AppealTicketRepository
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.AppealListQuery

final class TournamentAppealTables(
    appealTicketRepository: AppealTicketRepository
):
  def listAppeals(query: AppealListQuery): Vector[AppealTicket] =
    appealTicketRepository.findAll()
      .filter(ticket => query.status.forall(_ == ticket.status))
      .filter(ticket => query.priority.forall(_ == ticket.priority))
      .filter(ticket => query.tournamentId.forall(_ == ticket.tournamentId))
      .filter(ticket => query.stageId.forall(_ == ticket.stageId))
      .filter(ticket => query.tableId.forall(_ == ticket.tableId))
      .filter(ticket => query.openedBy.forall(_ == ticket.openedBy))
      .filter(ticket => query.assigneeId.forall(ticket.assigneeId.contains))
      .filter(ticket => !query.overdueOnly || ticket.dueAt.exists(_.isBefore(query.asOf)))
      .filter(ticket => query.dueBefore.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isAfter(limit))))
      .filter(ticket => query.dueAfter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isBefore(limit))))
      .sortBy(ticket => (ticket.updatedAt, ticket.id.value))

  def findAppeal(ticketId: AppealTicketId): Option[AppealTicket] =
    appealTicketRepository.findById(ticketId)

  def findActiveAppealForTable(tableId: TableId): Option[AppealTicket] =
    appealTicketRepository.findAll().find(ticket =>
      ticket.tableId == tableId &&
        (ticket.status == AppealStatus.Open ||
          ticket.status == AppealStatus.UnderReview ||
          ticket.status == AppealStatus.Escalated)
    )

object TournamentAppealTables:
  val OwnedTables: Vector[String] = Vector(
    "appeal_tickets"
  )
