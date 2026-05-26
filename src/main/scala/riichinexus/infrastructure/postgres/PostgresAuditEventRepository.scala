package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.audit.tables.auditevent.AuditEventTable

final class PostgresAuditEventRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AuditEventRepository:
  override def save(entry: AuditEventEntry): AuditEventEntry =
    connectionFactory.withConnection(AuditEventTable.save(_, entry))

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    connectionFactory.withConnection(AuditEventTable.findByAggregate(_, aggregateType, aggregateId))

  override def findAll(): Vector[AuditEventEntry] =
    connectionFactory.withConnection(AuditEventTable.findAll)

object PostgresAuditEventRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAuditEventRepository =
    new PostgresAuditEventRepository(connectionFactory)
