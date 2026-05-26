package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable

final class PostgresAppealTicketRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AppealTicketRepository:
  override def save(ticket: AppealTicket): AppealTicket =
    connectionFactory.withConnection(AppealTicketTable.save(_, ticket))

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    connectionFactory.withConnection(AppealTicketTable.findById(_, id))

  override def findAll(): Vector[AppealTicket] =
    connectionFactory.withConnection(AppealTicketTable.findAll)

object PostgresAppealTicketRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAppealTicketRepository =
    new PostgresAppealTicketRepository(connectionFactory)
