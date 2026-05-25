package riichinexus.infrastructure.memory

import riichinexus.application.ports.*
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
  private val state = InMemoryKeyValueStore[DomainEventOutboxRecordId, DomainEventOutboxRecord]()

  override def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    state.modify { currentState =>
      val (stateWithSequence, sequenceNo) =
        if record.sequenceNo > 0L then currentState -> record.sequenceNo
        else currentState.allocateSequenceNo
      val persisted = record.copy(
        sequenceNo = sequenceNo,
        version = InMemoryEventStoreLockSupport.nextVersion(
          "domain-event-outbox-record",
          record.id.value,
          record.version,
          currentState.get(record.id).map(_.version)
        )
      )
      stateWithSequence.upsert(persisted.id, persisted) -> persisted
    }

  override def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord] =
    state.get(id)

  override def findAll(): Vector[DomainEventOutboxRecord] =
    state.values.sortBy(_.sequenceNo)

object InMemoryDomainEventOutboxRepository:
  def apply(): InMemoryDomainEventOutboxRepository =
    new InMemoryDomainEventOutboxRepository()

final class InMemoryDomainEventDeliveryReceiptRepository extends DomainEventDeliveryReceiptRepository:
  private val state = InMemoryKeyValueStore[String, DomainEventDeliveryReceipt]()

  override def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    val key = compositeKey(receipt.outboxRecordId, receipt.subscriberId)
    state.modify { currentState =>
      currentState.get(key) match
        case Some(existing) => currentState -> existing
        case None =>
          val persisted = receipt.copy(
            version = InMemoryEventStoreLockSupport.nextVersion(
              "domain-event-delivery-receipt",
              receipt.id.value,
              receipt.version,
              None
            )
          )
          currentState.upsert(key, persisted) -> persisted
    }

  override def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt] =
    state.values.find(_.id == id)

  override def findAll(): Vector[DomainEventDeliveryReceipt] =
    state.values.sortBy(receipt => (receipt.deliveredAt, receipt.subscriberId, receipt.id.value))

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
  private val state = InMemoryKeyValueStore[String, DomainEventSubscriberCursor]()

  override def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    val key = compositeKey(cursor.subscriberId, cursor.partitionKey)
    state.modify { currentState =>
      val persisted = cursor.copy(
        version = InMemoryEventStoreLockSupport.nextVersion(
          "domain-event-subscriber-cursor",
          cursor.id.value,
          cursor.version,
          currentState.get(key).map(_.version)
        )
      )
      currentState.upsert(key, persisted) -> persisted
    }

  override def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor] =
    state.values.find(_.id == id)

  override def findAll(): Vector[DomainEventSubscriberCursor] =
    state.values.sortBy(cursor => (cursor.subscriberId, cursor.partitionKey))

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
  private val state = InMemoryAppendOnlyStore[AuditEventEntry]()

  override def save(entry: AuditEventEntry): AuditEventEntry =
    state.append(entry)

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    state.values.filter(entry => entry.aggregateType == aggregateType && entry.aggregateId == aggregateId)

  override def findAll(): Vector[AuditEventEntry] =
    state.values

object InMemoryAuditEventRepository:
  def apply(): InMemoryAuditEventRepository =
    new InMemoryAuditEventRepository()
