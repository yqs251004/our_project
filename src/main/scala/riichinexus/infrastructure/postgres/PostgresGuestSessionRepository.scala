package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable

final class PostgresGuestSessionRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GuestSessionRepository:
  override def save(session: GuestAccessSession): GuestAccessSession =
    connectionFactory.withConnection(GuestSessionTable.save(_, session))

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    connectionFactory.withConnection(GuestSessionTable.findById(_, id))

  override def findAll(): Vector[GuestAccessSession] =
    connectionFactory.withConnection(GuestSessionTable.findAll)

object PostgresGuestSessionRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGuestSessionRepository =
    new PostgresGuestSessionRepository(connectionFactory)
