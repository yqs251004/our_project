package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.events.tables.domaineventsubscribercursor.DomainEventSubscriberCursorTable

final class PostgresDomainEventSubscriberCursorRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventSubscriberCursorRepository:
  override def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    connectionFactory.withConnection(DomainEventSubscriberCursorTable.save(_, cursor))

  override def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor] =
    connectionFactory.withConnection(DomainEventSubscriberCursorTable.findById(_, id))

  override def findAll(): Vector[DomainEventSubscriberCursor] =
    connectionFactory.withConnection(DomainEventSubscriberCursorTable.findAll)

  override def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    connectionFactory.withConnection(
      DomainEventSubscriberCursorTable.findBySubscriberAndPartition(_, subscriberId, partitionKey)
    )

object PostgresDomainEventSubscriberCursorRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventSubscriberCursorRepository =
    new PostgresDomainEventSubscriberCursorRepository(connectionFactory)
