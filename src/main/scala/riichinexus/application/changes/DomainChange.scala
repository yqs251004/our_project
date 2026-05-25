package riichinexus.application.changes

import riichinexus.application.ports.*
import riichinexus.domain.event.DomainEvent
import riichinexus.domain.model.*

final case class DomainChange[A](
    aggregate: A,
    persist: A => A,
    auditEntries: A => Vector[AuditEventEntry] = (_: A) => Vector.empty,
    domainEvents: A => Vector[DomainEvent] = (_: A) => Vector.empty,
    outboxRecords: A => Vector[DomainEventOutboxRecord] = (_: A) => Vector.empty
)

object DomainChange:
  def save[A](
      aggregate: A
  )(
      persist: A => A
  ): DomainChange[A] =
    DomainChange(aggregate = aggregate, persist = persist)

  def audited[A](
      aggregate: A,
      persist: A => A,
      aggregateType: String,
      aggregateId: A => String,
      eventType: String,
      occurredAt: java.time.Instant,
      actorId: Option[PlayerId],
      details: A => Map[String, String],
      note: Option[String]
  ): DomainChange[A] =
    DomainChange(
      aggregate = aggregate,
      persist = persist,
      auditEntries = savedAggregate =>
        Vector(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = aggregateType,
            aggregateId = aggregateId(savedAggregate),
            eventType = eventType,
            occurredAt = occurredAt,
            actorId = actorId,
            details = details(savedAggregate),
            note = note
          )
        )
    )

final class DomainChangeInterpreter(
    transactionManager: TransactionManager,
    auditEventRepository: Option[AuditEventRepository] = None,
    eventBus: Option[DomainEventBus] = None,
    domainEventOutboxRepository: Option[DomainEventOutboxRepository] = None
):
  def commit[A](change: DomainChange[A]): A =
    transactionManager.inTransaction {
      commitWithinTransaction(change)
    }

  def commitWithinTransaction[A](change: DomainChange[A]): A =
    val savedAggregate = change.persist(change.aggregate)
    change.auditEntries(savedAggregate).foreach(entry =>
      auditEventRepository.foreach(_.save(entry))
    )
    change.outboxRecords(savedAggregate).foreach(record =>
      domainEventOutboxRepository.foreach(_.save(record))
    )
    change.domainEvents(savedAggregate).foreach(event =>
      eventBus.foreach(_.publish(event))
    )
    savedAggregate

  def commitAudited[A](
      aggregate: A,
      persist: A => A,
      aggregateType: String,
      aggregateId: A => String,
      eventType: String,
      occurredAt: java.time.Instant,
      actorId: Option[PlayerId],
      details: A => Map[String, String],
      note: Option[String]
  ): A =
    commitWithinTransaction(
      DomainChange.audited(
        aggregate = aggregate,
        persist = persist,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        eventType = eventType,
        occurredAt = occurredAt,
        actorId = actorId,
        details = details,
        note = note
      )
    )

object DomainChangeInterpreter:
  def auditAndEvents(
      transactionManager: TransactionManager,
      auditEventRepository: AuditEventRepository,
      eventBus: DomainEventBus
  ): DomainChangeInterpreter =
    DomainChangeInterpreter(
      transactionManager = transactionManager,
      auditEventRepository = Some(auditEventRepository),
      eventBus = Some(eventBus)
    )

  def auditOnly(
      transactionManager: TransactionManager,
      auditEventRepository: AuditEventRepository
  ): DomainChangeInterpreter =
    DomainChangeInterpreter(
      transactionManager = transactionManager,
      auditEventRepository = Some(auditEventRepository)
    )
