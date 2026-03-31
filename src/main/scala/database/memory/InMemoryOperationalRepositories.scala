package database.memory

import scala.collection.mutable

import ports.*
import riichinexus.domain.model.*

private object InMemoryRepositoryLockSupport:
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

final class InMemoryGuestSessionRepository extends GuestSessionRepository:
  private val state = mutable.LinkedHashMap.empty[GuestSessionId, GuestAccessSession]

  override def save(session: GuestAccessSession): GuestAccessSession =
    val persisted = session.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "guest-session",
        session.id.value,
        session.version,
        state.get(session.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    state.get(id)

  override def findAll(): Vector[GuestAccessSession] =
    state.values.toVector

object InMemoryGuestSessionRepository:
  def apply(): InMemoryGuestSessionRepository =
    new InMemoryGuestSessionRepository()

final class InMemoryMatchRecordRepository extends MatchRecordRepository:
  private val state = mutable.LinkedHashMap.empty[MatchRecordId, MatchRecord]

  override def save(record: MatchRecord): MatchRecord =
    state.update(record.id, record)
    record

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    state.get(id)

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    state.values.find(_.tableId == tableId)

  override def findAll(): Vector[MatchRecord] =
    state.values.toVector

object InMemoryMatchRecordRepository:
  def apply(): InMemoryMatchRecordRepository =
    new InMemoryMatchRecordRepository()

final class InMemoryPaifuRepository extends PaifuRepository:
  private val state = mutable.LinkedHashMap.empty[PaifuId, Paifu]

  override def save(paifu: Paifu): Paifu =
    state.update(paifu.id, paifu)
    paifu

  override def findById(id: PaifuId): Option[Paifu] =
    state.get(id)

  override def findAll(): Vector[Paifu] =
    state.values.toVector

object InMemoryPaifuRepository:
  def apply(): InMemoryPaifuRepository =
    new InMemoryPaifuRepository()

final class InMemoryEventCascadeRecordRepository extends EventCascadeRecordRepository:
  private val state = mutable.LinkedHashMap.empty[EventCascadeRecordId, EventCascadeRecord]

  override def save(record: EventCascadeRecord): EventCascadeRecord =
    val persisted = record.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "event-cascade-record",
        record.id.value,
        record.version,
        state.get(record.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    state.get(id)

  override def findAll(): Vector[EventCascadeRecord] =
    state.values.toVector.sortBy(record => (record.occurredAt, record.id.value))

object InMemoryEventCascadeRecordRepository:
  def apply(): InMemoryEventCascadeRecordRepository =
    new InMemoryEventCascadeRecordRepository()
