package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresDomainEventSubscriberCursorRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventSubscriberCursorRepository
    with JdbcRepositorySupport:
  override def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    val persisted = cursor.copy(version = cursor.version + 1)
    val sql =
      """
        |insert into domain_event_subscriber_cursors (
        |  id,
        |  subscriber_id,
        |  partition_key,
        |  last_outbox_record_id,
        |  last_sequence_no,
        |  advanced_at,
        |  payload,
        |  updated_at
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

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.subscriberId)
        statement.setString(3, persisted.partitionKey)
        statement.setString(4, persisted.lastDeliveredOutboxRecordId.value)
        statement.setLong(5, persisted.lastDeliveredSequenceNo)
        statement.setTimestamp(6, Timestamp.from(persisted.advancedAt))
        statement.setString(7, writeJson[DomainEventSubscriberCursor](persisted))
        statement.setInt(8, cursor.version)
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then read[DomainEventSubscriberCursor](resultSet.getString("payload"))
          else
            throw OptimisticConcurrencyException(
              aggregateType = "domain-event-subscriber-cursor",
              aggregateId = s"${cursor.subscriberId}:${cursor.partitionKey}",
              expectedVersion = cursor.version,
              actualVersion =
                findBySubscriberAndPartition(cursor.subscriberId, cursor.partitionKey).map(_.version)
            )
        }
      }
    }

  override def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor] =
    readOne[DomainEventSubscriberCursor](
      "select payload from domain_event_subscriber_cursors where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventSubscriberCursor] =
    readAll[DomainEventSubscriberCursor](
      "select payload from domain_event_subscriber_cursors order by subscriber_id asc, partition_key asc"
    )

  override def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    readOne[DomainEventSubscriberCursor](
      """
        |select payload
        |from domain_event_subscriber_cursors
        |where subscriber_id = ? and partition_key = ?
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, subscriberId)
        statement.setString(2, partitionKey)
      }
    )

object PostgresDomainEventSubscriberCursorRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventSubscriberCursorRepository =
    new PostgresDomainEventSubscriberCursorRepository(connectionFactory)
