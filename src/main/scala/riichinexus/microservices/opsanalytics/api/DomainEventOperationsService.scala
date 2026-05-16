package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class DomainEventOperationsService(
    outboxRepository: DomainEventOutboxRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val outboxMutationService = DomainEventOutboxMutationService(
    outboxRepository = outboxRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  def replayOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    outboxMutationService.replayOutboxRecord(
      recordId = recordId,
      actor = actor,
      replayAt = replayAt,
      note = note,
      at = at
    )

  def acknowledgeOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    outboxMutationService.acknowledgeOutboxRecord(
      recordId = recordId,
      actor = actor,
      note = note,
      at = at
    )

  def quarantineOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    outboxMutationService.quarantineOutboxRecord(
      recordId = recordId,
      actor = actor,
      reason = reason,
      at = at
    )

  def replayOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    outboxMutationService.replayOutboxRecords(
      recordIds = recordIds,
      actor = actor,
      replayAt = replayAt,
      note = note,
      at = at
    )

  def acknowledgeOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    outboxMutationService.acknowledgeOutboxRecords(
      recordIds = recordIds,
      actor = actor,
      note = note,
      at = at
    )

  def quarantineOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    outboxMutationService.quarantineOutboxRecords(
      recordIds = recordIds,
      actor = actor,
      reason = reason,
      at = at
    )
