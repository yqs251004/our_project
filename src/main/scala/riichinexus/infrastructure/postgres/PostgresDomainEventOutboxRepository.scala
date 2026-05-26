package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.events.tables.domaineventoutbox.DomainEventOutboxTable

final class PostgresDomainEventOutboxRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventOutboxRepository:
  override def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    connectionFactory.withConnection(DomainEventOutboxTable.save(_, record))

  override def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord] =
    connectionFactory.withConnection(DomainEventOutboxTable.findById(_, id))

  override def findAll(): Vector[DomainEventOutboxRecord] =
    connectionFactory.withConnection(DomainEventOutboxTable.findAll)

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[DomainEventOutboxRecord] =
    connectionFactory.withConnection(DomainEventOutboxTable.findPending(_, limit, asOf))

object PostgresDomainEventOutboxRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventOutboxRepository =
    new PostgresDomainEventOutboxRepository(connectionFactory)
