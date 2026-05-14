package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresDomainEventOutboxRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventOutboxRepository
    with JdbcRepositorySupport:
  override def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    val persisted = record.copy(
      sequenceNo =
        if record.sequenceNo > 0L then record.sequenceNo
        else nextSequenceNo(),
      version = record.version + 1
    )
    val sql =
      """
        |insert into domain_event_outbox (
        |  id,
        |  sequence_no,
        |  event_type,
        |  aggregate_type,
        |  aggregate_id,
        |  status,
        |  occurred_at,
        |  available_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  sequence_no = excluded.sequence_no,
        |  event_type = excluded.event_type,
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  status = excluded.status,
        |  occurred_at = excluded.occurred_at,
        |  available_at = excluded.available_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(domain_event_outbox.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setLong(2, persisted.sequenceNo)
        statement.setString(3, persisted.eventType)
        statement.setString(4, persisted.aggregateType)
        statement.setString(5, persisted.aggregateId)
        statement.setString(6, persisted.status.toString)
        statement.setTimestamp(7, Timestamp.from(persisted.occurredAt))
        statement.setTimestamp(8, Timestamp.from(persisted.availableAt))
        statement.setString(9, writeJson[DomainEventOutboxRecord](persisted))
        statement.setInt(10, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "domain-event-outbox-record",
      persisted.id.value,
      record.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord] =
    readOne[DomainEventOutboxRecord](
      "select payload from domain_event_outbox where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventOutboxRecord] =
    readAll[DomainEventOutboxRecord](
      "select payload from domain_event_outbox order by sequence_no asc"
    )

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[DomainEventOutboxRecord] =
    readAll[DomainEventOutboxRecord](
      """
        |select payload
        |from domain_event_outbox
        |where status = 'Pending' and available_at <= ?
        |order by sequence_no asc
        |limit ?
        |""".stripMargin,
      { statement =>
        statement.setTimestamp(1, Timestamp.from(asOf))
        statement.setInt(2, limit)
      }
    )

  private def nextSequenceNo(): Long =
    withConnection { connection =>
      Using.resource(connection.prepareStatement("select nextval('domain_event_outbox_sequence') as sequence_no")) {
        statement =>
          Using.resource(statement.executeQuery()) { resultSet =>
            resultSet.next()
            resultSet.getLong("sequence_no")
          }
      }
    }

object PostgresDomainEventOutboxRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventOutboxRepository =
    new PostgresDomainEventOutboxRepository(connectionFactory)
