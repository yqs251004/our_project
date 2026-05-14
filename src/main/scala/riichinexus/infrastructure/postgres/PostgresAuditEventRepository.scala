package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresAuditEventRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AuditEventRepository
    with JdbcRepositorySupport:
  override def save(entry: AuditEventEntry): AuditEventEntry =
    val sql =
      """
        |insert into audit_events (id, aggregate_type, aggregate_id, event_type, occurred_at, actor_id, payload)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
        |on conflict (id) do update set
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  event_type = excluded.event_type,
        |  occurred_at = excluded.occurred_at,
        |  actor_id = excluded.actor_id,
        |  payload = excluded.payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, entry.id.value)
        statement.setString(2, entry.aggregateType)
        statement.setString(3, entry.aggregateId)
        statement.setString(4, entry.eventType)
        statement.setTimestamp(5, Timestamp.from(entry.occurredAt))
        setNullableString(statement, 6, entry.actorId.map(_.value))
        statement.setString(7, writeJson[AuditEventEntry](entry))
        statement.executeUpdate()
      }
    }

    entry

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    readAll[AuditEventEntry](
      "select payload from audit_events where aggregate_type = ? and aggregate_id = ? order by occurred_at desc",
      { statement =>
        statement.setString(1, aggregateType)
        statement.setString(2, aggregateId)
      }
    )

  override def findAll(): Vector[AuditEventEntry] =
    readAll[AuditEventEntry]("select payload from audit_events order by occurred_at desc")

object PostgresAuditEventRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAuditEventRepository =
    new PostgresAuditEventRepository(connectionFactory)
