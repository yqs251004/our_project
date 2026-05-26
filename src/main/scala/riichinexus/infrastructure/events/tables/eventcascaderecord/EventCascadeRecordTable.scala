package riichinexus.infrastructure.events.tables.eventcascaderecord

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object EventCascadeRecordTable:
  private val upsertSql: String =
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

  private[riichinexus] def save(connection: Connection, record: EventCascadeRecord): EventCascadeRecord =
    val persisted = record.copy(version = record.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.consumer.toString)
      statement.setString(3, persisted.status.toString)
      statement.setString(4, persisted.aggregateType)
      statement.setString(5, persisted.aggregateId)
      statement.setTimestamp(6, Timestamp.from(persisted.occurredAt))
      statement.setString(7, write[EventCascadeRecord](persisted))
      statement.setInt(8, record.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "event-cascade-record",
        aggregateId = persisted.id.value,
        expectedVersion = record.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from event_cascade_records
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(connection: Connection, id: EventCascadeRecordId): Option[EventCascadeRecord] =
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
      |from event_cascade_records
      |order by occurred_at desc, id desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[EventCascadeRecord] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readRecords)
    }

  private def readRecords(resultSet: ResultSet): Vector[EventCascadeRecord] =
    @tailrec
    def loop(acc: Vector[EventCascadeRecord]): Vector[EventCascadeRecord] =
      if resultSet.next() then loop(readRecord(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readRecord(resultSet: ResultSet): EventCascadeRecord =
    read[EventCascadeRecord](resultSet.getString("payload"))
