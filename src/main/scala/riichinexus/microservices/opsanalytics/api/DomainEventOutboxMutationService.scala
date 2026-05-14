package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.{AuditEventRepository, DomainEventOutboxRepository, TransactionManager}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationService

private[api] final class DomainEventOutboxMutationService(
    outboxRepository: DomainEventOutboxRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
):
  def replayOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be replayed, but ${recordId.value} is ${record.status}"
      )

      val replayed = outboxRepository.save(record.markReplayed(replayAt))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxReplayed",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "replayAt" -> replayAt.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = note
        )
      )
      replayed
    }

  def acknowledgeOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be acknowledged, but ${recordId.value} is ${record.status}"
      )

      val acknowledged = outboxRepository.save(record.markCompleted(at))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxAcknowledged",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = note
        )
      )
      acknowledged
    }

  def quarantineOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val normalizedReason = reason.trim
      require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        record.status != DomainEventOutboxStatus.Completed,
        s"Completed outbox record ${recordId.value} cannot be quarantined"
      )
      require(
        record.status != DomainEventOutboxStatus.Quarantined,
        s"Outbox record ${recordId.value} is already quarantined"
      )

      val quarantined = outboxRepository.save(record.markQuarantined(normalizedReason, at))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxQuarantined",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = Some(normalizedReason)
        )
      )
      quarantined
    }

  def replayOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "replay",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      replayOutboxRecord(recordId, actor, replayAt, note, at)
    }

  def acknowledgeOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "ack",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      acknowledgeOutboxRecord(recordId, actor, note, at)
    }

  def quarantineOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "quarantine",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      quarantineOutboxRecord(recordId, actor, reason, at)
    }

  private def runBatchOperation(
      action: String,
      recordIds: Vector[DomainEventOutboxRecordId],
      processedAt: Instant
  )(
      operation: DomainEventOutboxRecordId => DomainEventOutboxRecord
  ): DomainEventOutboxBatchOperationResult =
    val normalizedIds = recordIds.distinct
    require(normalizedIds.nonEmpty, s"Domain event outbox batch $action requires at least one recordId")

    val failures = Vector.newBuilder[DomainEventOutboxOperationFailure]
    val succeededIds = Vector.newBuilder[DomainEventOutboxRecordId]

    normalizedIds.foreach { recordId =>
      try
        operation(recordId)
        succeededIds += recordId
      catch
        case error: IllegalArgumentException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
        case error: NoSuchElementException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
        case error: IllegalStateException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
    }

    DomainEventOutboxBatchOperationResult(
      action = action,
      processedAt = processedAt,
      requestedCount = normalizedIds.size,
      succeededRecordIds = succeededIds.result(),
      failures = failures.result()
    )
