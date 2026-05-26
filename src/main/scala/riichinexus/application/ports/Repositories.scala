package riichinexus.application.ports

import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.*

trait EventCascadeRecordRepository:
  def save(record: EventCascadeRecord): EventCascadeRecord
  def findById(id: EventCascadeRecordId): Option[EventCascadeRecord]
  def findAll(): Vector[EventCascadeRecord]

  def findPending(limit: Int): Vector[EventCascadeRecord] =
    findAll()
      .filter(_.status == EventCascadeStatus.Pending)
      .sortBy(_.occurredAt)
      .take(limit)

  def findByAggregate(aggregateType: String, aggregateId: String): Vector[EventCascadeRecord] =
    findAll().filter(record => record.aggregateType == aggregateType && record.aggregateId == aggregateId)

trait DomainEventOutboxRepository:
  def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord
  def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord]
  def findAll(): Vector[DomainEventOutboxRecord]

  def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[DomainEventOutboxRecord] =
    findAll()
      .filter(_.isRunnable(asOf))
      .sortBy(_.sequenceNo)
      .take(limit)

trait DomainEventDeliveryReceiptRepository:
  def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt
  def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt]
  def findAll(): Vector[DomainEventDeliveryReceipt]

  def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    findAll().find(receipt =>
      receipt.outboxRecordId == outboxRecordId && receipt.subscriberId == subscriberId
    )

trait DomainEventSubscriberCursorRepository:
  def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor
  def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor]
  def findAll(): Vector[DomainEventSubscriberCursor]

  def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    findAll().find(cursor =>
      cursor.subscriberId == subscriberId && cursor.partitionKey == partitionKey
    )

trait AuditEventRepository:
  def save(entry: AuditEventEntry): AuditEventEntry
  def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry]
  def findAll(): Vector[AuditEventEntry]
