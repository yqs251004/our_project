package database.memory

import scala.collection.mutable

import ports.*
import riichinexus.domain.model.*

private object InMemoryEventStoreLockSupport:
  def nextVersion(
      aggregateType: String,
      aggregateId: String,
      incomingVersion: Int,
      currentVersion: Option[Int]
  ): Int =
    currentVersion match
      case None =>
        if incomingVersion != 0 then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, None)
        1
      case Some(actual) =>
        if actual != incomingVersion then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, Some(actual))
        actual + 1

final class InMemoryDomainEventOutboxRepository extends DomainEventOutboxRepository:
  private val state = mutable.LinkedHashMap.empty[DomainEventOutboxRecordId, DomainEventOutboxRecord]
  private var nextSequenceNo: Long = 1L

  override def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    val persisted = record.copy(
      sequenceNo =
        if record.sequenceNo > 0L then record.sequenceNo
        else
          val allocated = nextSequenceNo
          nextSequenceNo += 1L
          allocated,
      version = InMemoryEventStoreLockSupport.nextVersion(
        "domain-event-outbox-record",
        record.id.value,
        record.version,
        state.get(record.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord] =
    state.get(id)

  override def findAll(): Vector[DomainEventOutboxRecord] =
    state.values.toVector.sortBy(_.sequenceNo)

object InMemoryDomainEventOutboxRepository:
  def apply(): InMemoryDomainEventOutboxRepository =
    new InMemoryDomainEventOutboxRepository()

final class InMemoryDomainEventDeliveryReceiptRepository extends DomainEventDeliveryReceiptRepository:
  private val state = mutable.LinkedHashMap.empty[String, DomainEventDeliveryReceipt]

  override def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    val key = compositeKey(receipt.outboxRecordId, receipt.subscriberId)
    state.get(key) match
      case Some(existing) => existing
      case None =>
        val persisted = receipt.copy(
          version = InMemoryEventStoreLockSupport.nextVersion(
            "domain-event-delivery-receipt",
            receipt.id.value,
            receipt.version,
            None
          )
        )
        state.update(key, persisted)
        persisted

  override def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt] =
    state.values.find(_.id == id)

  override def findAll(): Vector[DomainEventDeliveryReceipt] =
    state.values.toVector.sortBy(receipt => (receipt.deliveredAt, receipt.subscriberId, receipt.id.value))

  override def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    state.get(compositeKey(outboxRecordId, subscriberId))

  private def compositeKey(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): String =
    s"${outboxRecordId.value}::$subscriberId"

object InMemoryDomainEventDeliveryReceiptRepository:
  def apply(): InMemoryDomainEventDeliveryReceiptRepository =
    new InMemoryDomainEventDeliveryReceiptRepository()

final class InMemoryDomainEventSubscriberCursorRepository extends DomainEventSubscriberCursorRepository:
  private val state = mutable.LinkedHashMap.empty[String, DomainEventSubscriberCursor]

  override def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    val key = compositeKey(cursor.subscriberId, cursor.partitionKey)
    val persisted = cursor.copy(
      version = InMemoryEventStoreLockSupport.nextVersion(
        "domain-event-subscriber-cursor",
        cursor.id.value,
        cursor.version,
        state.get(key).map(_.version)
      )
    )
    state.update(key, persisted)
    persisted

  override def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor] =
    state.values.find(_.id == id)

  override def findAll(): Vector[DomainEventSubscriberCursor] =
    state.values.toVector.sortBy(cursor => (cursor.subscriberId, cursor.partitionKey))

  override def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    state.get(compositeKey(subscriberId, partitionKey))

  private def compositeKey(subscriberId: String, partitionKey: String): String =
    s"$subscriberId::$partitionKey"

object InMemoryDomainEventSubscriberCursorRepository:
  def apply(): InMemoryDomainEventSubscriberCursorRepository =
    new InMemoryDomainEventSubscriberCursorRepository()

final class InMemoryAuditEventRepository extends AuditEventRepository:
  private val state = mutable.ArrayBuffer.empty[AuditEventEntry]

  override def save(entry: AuditEventEntry): AuditEventEntry =
    state += entry
    entry

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    state.filter(entry => entry.aggregateType == aggregateType && entry.aggregateId == aggregateId).toVector

  override def findAll(): Vector[AuditEventEntry] =
    state.toVector

object InMemoryAuditEventRepository:
  def apply(): InMemoryAuditEventRepository =
    new InMemoryAuditEventRepository()
