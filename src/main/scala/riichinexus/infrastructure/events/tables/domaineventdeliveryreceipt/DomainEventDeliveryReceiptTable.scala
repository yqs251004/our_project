package riichinexus.infrastructure.events.tables.domaineventdeliveryreceipt

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object DomainEventDeliveryReceiptTable:
  private val upsertSql: String =
    """
      |insert into domain_event_delivery_receipts (
      |  id, outbox_record_id, subscriber_id, event_type, delivered_at, payload, updated_at
      |)
      |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (outbox_record_id, subscriber_id) do update set
      |  event_type = domain_event_delivery_receipts.event_type,
      |  delivered_at = domain_event_delivery_receipts.delivered_at,
      |  payload = domain_event_delivery_receipts.payload,
      |  updated_at = domain_event_delivery_receipts.updated_at
      |returning payload
      |""".stripMargin

  private[riichinexus] def save(
      connection: Connection,
      receipt: DomainEventDeliveryReceipt
  ): DomainEventDeliveryReceipt =
    val persisted = receipt.copy(version = receipt.version + 1)
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.outboxRecordId.value)
      statement.setString(3, persisted.subscriberId)
      statement.setString(4, persisted.eventType)
      statement.setTimestamp(5, Timestamp.from(persisted.deliveredAt))
      statement.setString(6, write[DomainEventDeliveryReceipt](persisted))
      Using.resource(statement.executeQuery()) { resultSet =>
        resultSet.next()
        read[DomainEventDeliveryReceipt](resultSet.getString("payload"))
      }
    }

  private val findByIdSql: String =
    """
      |select payload
      |from domain_event_delivery_receipts
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(
      connection: Connection,
      id: DomainEventDeliveryReceiptId
  ): Option[DomainEventDeliveryReceipt] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readReceipt(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from domain_event_delivery_receipts
      |order by delivered_at desc, id desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[DomainEventDeliveryReceipt] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readReceipts)
    }

  private val findByOutboxRecordAndSubscriberSql: String =
    """
      |select payload
      |from domain_event_delivery_receipts
      |where outbox_record_id = ? and subscriber_id = ?
      |limit 1
      |""".stripMargin

  private[riichinexus] def findByOutboxRecordAndSubscriber(
      connection: Connection,
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    Using.resource(connection.prepareStatement(findByOutboxRecordAndSubscriberSql)) { statement =>
      statement.setString(1, outboxRecordId.value)
      statement.setString(2, subscriberId)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readReceipt(resultSet))
        else None
      }
    }

  private def readReceipts(resultSet: ResultSet): Vector[DomainEventDeliveryReceipt] =
    @tailrec
    def loop(acc: Vector[DomainEventDeliveryReceipt]): Vector[DomainEventDeliveryReceipt] =
      if resultSet.next() then loop(readReceipt(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readReceipt(resultSet: ResultSet): DomainEventDeliveryReceipt =
    read[DomainEventDeliveryReceipt](resultSet.getString("payload"))
