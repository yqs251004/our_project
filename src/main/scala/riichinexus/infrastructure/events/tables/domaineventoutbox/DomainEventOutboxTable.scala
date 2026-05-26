package riichinexus.infrastructure.events.tables.domaineventoutbox

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.Instant

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object DomainEventOutboxTable:
  private val upsertSql: String =
    """
      |insert into domain_event_outbox (
      |  id, sequence_no, event_type, aggregate_type, aggregate_id, status,
      |  occurred_at, available_at, payload, updated_at
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

  private[riichinexus] def save(connection: Connection, record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    val persisted = record.copy(
      sequenceNo = if record.sequenceNo > 0L then record.sequenceNo else nextSequenceNo(connection),
      version = record.version + 1
    )
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setLong(2, persisted.sequenceNo)
      statement.setString(3, persisted.eventType)
      statement.setString(4, persisted.aggregateType)
      statement.setString(5, persisted.aggregateId)
      statement.setString(6, persisted.status.toString)
      statement.setTimestamp(7, Timestamp.from(persisted.occurredAt))
      statement.setTimestamp(8, Timestamp.from(persisted.availableAt))
      statement.setString(9, write[DomainEventOutboxRecord](persisted))
      statement.setInt(10, record.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "domain-event-outbox-record",
        aggregateId = persisted.id.value,
        expectedVersion = record.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from domain_event_outbox
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(
      connection: Connection,
      id: DomainEventOutboxRecordId
  ): Option[DomainEventOutboxRecord] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readRecord(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from domain_event_outbox
      |order by sequence_no asc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[DomainEventOutboxRecord] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readRecords)
    }

  private val findPendingSql: String =
    """
      |select payload
      |from domain_event_outbox
      |where status = 'Pending' and available_at <= ?
      |order by sequence_no asc
      |limit ?
      |""".stripMargin

  private[riichinexus] def findPending(
      connection: Connection,
      limit: Int,
      asOf: Instant = Instant.now()
  ): Vector[DomainEventOutboxRecord] =
    Using.resource(connection.prepareStatement(findPendingSql)) { statement =>
      statement.setTimestamp(1, Timestamp.from(asOf))
      statement.setInt(2, limit)
      Using.resource(statement.executeQuery())(readRecords)
    }

  private val nextSequenceNoSql: String =
    """
      |select nextval('domain_event_outbox_sequence') as sequence_no
      |""".stripMargin

  private def nextSequenceNo(connection: Connection): Long =
    Using.resource(connection.prepareStatement(nextSequenceNoSql)) { statement =>
      Using.resource(statement.executeQuery()) { resultSet =>
        resultSet.next()
        resultSet.getLong("sequence_no")
      }
    }

  private def readRecords(resultSet: ResultSet): Vector[DomainEventOutboxRecord] =
    @tailrec
    def loop(acc: Vector[DomainEventOutboxRecord]): Vector[DomainEventOutboxRecord] =
      if resultSet.next() then loop(readRecord(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readRecord(resultSet: ResultSet): DomainEventOutboxRecord =
    read[DomainEventOutboxRecord](resultSet.getString("payload"))
