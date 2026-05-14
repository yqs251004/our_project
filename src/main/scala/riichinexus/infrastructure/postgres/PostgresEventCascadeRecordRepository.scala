package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresEventCascadeRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends EventCascadeRecordRepository
    with JdbcRepositorySupport:
  override def save(record: EventCascadeRecord): EventCascadeRecord =
    val persisted = record.copy(version = record.version + 1)
    val sql =
      """
        |insert into event_cascade_records (id, consumer, status, aggregate_type, aggregate_id, occurred_at, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  consumer = excluded.consumer,
        |  status = excluded.status,
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  occurred_at = excluded.occurred_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(event_cascade_records.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.consumer.toString)
        statement.setString(3, persisted.status.toString)
        statement.setString(4, persisted.aggregateType)
        statement.setString(5, persisted.aggregateId)
        statement.setTimestamp(6, Timestamp.from(persisted.occurredAt))
        statement.setString(7, writeJson[EventCascadeRecord](persisted))
        statement.setInt(8, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "event-cascade-record",
      persisted.id.value,
      record.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    readOne[EventCascadeRecord](
      "select payload from event_cascade_records where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[EventCascadeRecord] =
    readAll[EventCascadeRecord](
      "select payload from event_cascade_records order by occurred_at desc, id desc"
    )

object PostgresEventCascadeRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresEventCascadeRecordRepository =
    new PostgresEventCascadeRecordRepository(connectionFactory)
