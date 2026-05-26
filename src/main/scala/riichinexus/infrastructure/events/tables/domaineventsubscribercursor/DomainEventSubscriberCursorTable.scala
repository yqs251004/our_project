package riichinexus.infrastructure.events.tables.domaineventsubscribercursor

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object DomainEventSubscriberCursorTable:
  private val upsertSql: String =
    """
      |insert into domain_event_subscriber_cursors (
      |  id, subscriber_id, partition_key, last_outbox_record_id,
      |  last_sequence_no, advanced_at, payload, updated_at
      |)
      |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (subscriber_id, partition_key) do update set
      |  last_outbox_record_id = excluded.last_outbox_record_id,
      |  last_sequence_no = excluded.last_sequence_no,
      |  advanced_at = excluded.advanced_at,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(domain_event_subscriber_cursors.payload ->> 'version' as integer) = ?
      |returning payload
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    val persisted = cursor.copy(version = cursor.version + 1)
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.subscriberId)
      statement.setString(3, persisted.partitionKey)
      statement.setString(4, persisted.lastDeliveredOutboxRecordId.value)
      statement.setLong(5, persisted.lastDeliveredSequenceNo)
      statement.setTimestamp(6, Timestamp.from(persisted.advancedAt))
      statement.setString(7, write[DomainEventSubscriberCursor](persisted))
      statement.setInt(8, cursor.version)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then read[DomainEventSubscriberCursor](resultSet.getString("payload"))
        else
          throw OptimisticConcurrencyException(
            aggregateType = "domain-event-subscriber-cursor",
            aggregateId = s"${cursor.subscriberId}:${cursor.partitionKey}",
            expectedVersion = cursor.version,
            actualVersion = findBySubscriberAndPartition(connection, cursor.subscriberId, cursor.partitionKey).map(_.version)
          )
      }
    }

  private val findByIdSql: String =
    """
      |select payload
      |from domain_event_subscriber_cursors
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(
      connection: Connection,
      id: DomainEventSubscriberCursorId
  ): Option[DomainEventSubscriberCursor] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readCursor(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from domain_event_subscriber_cursors
      |order by subscriber_id asc, partition_key asc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[DomainEventSubscriberCursor] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readCursors)
    }

  private val findBySubscriberAndPartitionSql: String =
    """
      |select payload
      |from domain_event_subscriber_cursors
      |where subscriber_id = ? and partition_key = ?
      |limit 1
      |""".stripMargin

  private[riichinexus] def findBySubscriberAndPartition(
      connection: Connection,
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    Using.resource(connection.prepareStatement(findBySubscriberAndPartitionSql)) { statement =>
      statement.setString(1, subscriberId)
      statement.setString(2, partitionKey)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readCursor(resultSet))
        else None
      }
    }

  private def readCursors(resultSet: ResultSet): Vector[DomainEventSubscriberCursor] =
    @tailrec
    def loop(acc: Vector[DomainEventSubscriberCursor]): Vector[DomainEventSubscriberCursor] =
      if resultSet.next() then loop(readCursor(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readCursor(resultSet: ResultSet): DomainEventSubscriberCursor =
    read[DomainEventSubscriberCursor](resultSet.getString("payload"))
