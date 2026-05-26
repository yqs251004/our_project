package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.events.tables.domaineventdeliveryreceipt.DomainEventDeliveryReceiptTable

final class PostgresDomainEventDeliveryReceiptRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventDeliveryReceiptRepository:
  override def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    connectionFactory.withConnection(DomainEventDeliveryReceiptTable.save(_, receipt))

  override def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt] =
    connectionFactory.withConnection(DomainEventDeliveryReceiptTable.findById(_, id))

  override def findAll(): Vector[DomainEventDeliveryReceipt] =
    connectionFactory.withConnection(DomainEventDeliveryReceiptTable.findAll)

  override def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    connectionFactory.withConnection(
      DomainEventDeliveryReceiptTable.findByOutboxRecordAndSubscriber(_, outboxRecordId, subscriberId)
    )

object PostgresDomainEventDeliveryReceiptRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventDeliveryReceiptRepository =
    new PostgresDomainEventDeliveryReceiptRepository(connectionFactory)
