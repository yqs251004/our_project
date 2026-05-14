package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresDomainEventDeliveryReceiptRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventDeliveryReceiptRepository
    with JdbcRepositorySupport:
  override def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    val persisted = receipt.copy(version = receipt.version + 1)
    val sql =
      """
        |insert into domain_event_delivery_receipts (
        |  id,
        |  outbox_record_id,
        |  subscriber_id,
        |  event_type,
        |  delivered_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (outbox_record_id, subscriber_id) do update set
        |  event_type = domain_event_delivery_receipts.event_type,
        |  delivered_at = domain_event_delivery_receipts.delivered_at,
        |  payload = domain_event_delivery_receipts.payload,
        |  updated_at = domain_event_delivery_receipts.updated_at
        |returning payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.outboxRecordId.value)
        statement.setString(3, persisted.subscriberId)
        statement.setString(4, persisted.eventType)
        statement.setTimestamp(5, Timestamp.from(persisted.deliveredAt))
        statement.setString(6, writeJson[DomainEventDeliveryReceipt](persisted))
        Using.resource(statement.executeQuery()) { resultSet =>
          resultSet.next()
          read[DomainEventDeliveryReceipt](resultSet.getString("payload"))
        }
      }
    }

  override def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt] =
    readOne[DomainEventDeliveryReceipt](
      "select payload from domain_event_delivery_receipts where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventDeliveryReceipt] =
    readAll[DomainEventDeliveryReceipt](
      "select payload from domain_event_delivery_receipts order by delivered_at desc, id desc"
    )

  override def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    readOne[DomainEventDeliveryReceipt](
      """
        |select payload
        |from domain_event_delivery_receipts
        |where outbox_record_id = ? and subscriber_id = ?
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, outboxRecordId.value)
        statement.setString(2, subscriberId)
      }
    )

object PostgresDomainEventDeliveryReceiptRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventDeliveryReceiptRepository =
    new PostgresDomainEventDeliveryReceiptRepository(connectionFactory)
