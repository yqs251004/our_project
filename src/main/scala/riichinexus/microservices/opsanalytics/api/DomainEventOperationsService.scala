package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.opsanalytics.api.responses.DomainEventOutboxHistoryView

final class DomainEventOperationsService(
    outboxRepository: DomainEventOutboxRepository,
    deliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    subscriberCursorRepository: DomainEventSubscriberCursorRepository,
    subscribers: Vector[DomainEventSubscriber],
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val subscriberStatusService = DomainEventSubscriberStatusService(
    outboxRepository = outboxRepository,
    deliveryReceiptRepository = deliveryReceiptRepository,
    subscriberCursorRepository = subscriberCursorRepository,
    subscribers = subscribers
  )

  private val outboxQueryService = DomainEventOutboxQueryService(
    outboxRepository = outboxRepository,
    deliveryReceiptRepository = deliveryReceiptRepository,
    auditEventRepository = auditEventRepository,
    authorizationService = authorizationService,
    subscriberStatusService = subscriberStatusService
  )

  private val outboxMutationService = DomainEventOutboxMutationService(
    outboxRepository = outboxRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  def outboxHistory(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal
  ): DomainEventOutboxHistoryView =
    outboxQueryService.outboxHistory(recordId = recordId, actor = actor)

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

  def summary(asOf: Instant = Instant.now()): DomainEventBusSummary =
    outboxQueryService.summary(asOf = asOf)

  def outboxRecords(
      asOf: Instant = Instant.now(),
      status: Option[DomainEventOutboxStatus] = None,
      eventType: Option[String] = None,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      subscriberId: Option[String] = None,
      partitionKey: Option[String] = None,
      delivered: Option[Boolean] = None,
      blockedOnly: Boolean = false
  ): Vector[DomainEventOutboxRecord] =
    outboxQueryService.outboxRecords(
      asOf = asOf,
      status = status,
      eventType = eventType,
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      subscriberId = subscriberId,
      partitionKey = partitionKey,
      delivered = delivered,
      blockedOnly = blockedOnly
    )

  def subscriberStatuses(
      asOf: Instant = Instant.now(),
      subscriberId: Option[String] = None
  ): Vector[DomainEventSubscriberStatus] =
    subscriberStatusService.subscriberStatuses(asOf = asOf, subscriberId = subscriberId)

  def subscriberPartitionStatuses(
      subscriberId: String,
      asOf: Instant = Instant.now(),
      lagOnly: Boolean = false,
      blockedOnly: Boolean = false,
      partitionKey: Option[String] = None
  ): Vector[DomainEventSubscriberPartitionStatus] =
    subscriberStatusService.subscriberPartitionStatuses(
      subscriberId = subscriberId,
      asOf = asOf,
      lagOnly = lagOnly,
      blockedOnly = blockedOnly,
      partitionKey = partitionKey
    )
