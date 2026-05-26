package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.events.tables.eventcascaderecord.EventCascadeRecordTable

final class PostgresEventCascadeRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends EventCascadeRecordRepository:
  override def save(record: EventCascadeRecord): EventCascadeRecord =
    connectionFactory.withConnection(EventCascadeRecordTable.save(_, record))

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    connectionFactory.withConnection(EventCascadeRecordTable.findById(_, id))

  override def findAll(): Vector[EventCascadeRecord] =
    connectionFactory.withConnection(EventCascadeRecordTable.findAll)

object PostgresEventCascadeRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresEventCascadeRecordRepository =
    new PostgresEventCascadeRecordRepository(connectionFactory)
