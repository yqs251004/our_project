package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.api.requests.*
import riichinexus.microservices.opsanalytics.api.responses.DomainEventOutboxHistoryView
import riichinexus.microservices.opsanalytics.objects.{DomainEventOutboxQuery, EventCascadeRecordQuery}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables

object DomainEventOpsApi:

  def summary(
      service: DomainEventQueryService,
      asOf: Instant
  ): DomainEventBusSummary =
    service.summary(asOf)

  def outboxRecords(
      service: DomainEventQueryService,
      query: DomainEventOutboxQuery
  ): Vector[DomainEventOutboxRecord] =
    service.outboxRecords(
      asOf = query.asOf,
      status = query.status,
      eventType = query.eventType,
      aggregateType = query.aggregateType,
      aggregateId = query.aggregateId,
      subscriberId = query.subscriberId,
      partitionKey = query.partitionKey,
      delivered = query.delivered,
      blockedOnly = query.blockedOnly
    )

  def replayOutboxRecords(
      service: DomainEventOperationsService,
      actor: AccessPrincipal,
      request: BatchReplayDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    service.replayOutboxRecords(
      recordIds = request.records,
      actor = actor,
      replayAt = request.replayAtInstant.getOrElse(Instant.now()),
      note = request.note,
      at = at
    )

  def acknowledgeOutboxRecords(
      service: DomainEventOperationsService,
      actor: AccessPrincipal,
      request: BatchAcknowledgeDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    service.acknowledgeOutboxRecords(
      recordIds = request.records,
      actor = actor,
      note = request.note,
      at = at
    )

  def quarantineOutboxRecords(
      service: DomainEventOperationsService,
      actor: AccessPrincipal,
      request: BatchQuarantineDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    service.quarantineOutboxRecords(
      recordIds = request.records,
      actor = actor,
      reason = request.reason,
      at = at
    )

  def outboxHistory(
      service: DomainEventQueryService,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal
  ): DomainEventOutboxHistoryView =
    service.outboxHistory(recordId = recordId, actor = actor)

  def replayOutboxRecord(
      service: DomainEventOperationsService,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      request: ReplayDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    service.replayOutboxRecord(
      recordId = recordId,
      actor = actor,
      replayAt = request.replayAtInstant.getOrElse(Instant.now()),
      note = request.note,
      at = at
    )

  def acknowledgeOutboxRecord(
      service: DomainEventOperationsService,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      request: AcknowledgeDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    service.acknowledgeOutboxRecord(
      recordId = recordId,
      actor = actor,
      note = request.note,
      at = at
    )

  def quarantineOutboxRecord(
      service: DomainEventOperationsService,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      request: QuarantineDomainEventOutboxRequest,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    service.quarantineOutboxRecord(
      recordId = recordId,
      actor = actor,
      reason = request.reason,
      at = at
    )

  def subscriberStatuses(
      service: DomainEventQueryService,
      asOf: Instant,
      subscriberId: Option[String]
  ): Vector[DomainEventSubscriberStatus] =
    service.subscriberStatuses(asOf = asOf, subscriberId = subscriberId)

  def subscriberPartitionStatuses(
      service: DomainEventQueryService,
      subscriberId: String,
      asOf: Instant,
      lagOnly: Boolean,
      blockedOnly: Boolean,
      partitionKey: Option[String]
  ): Vector[DomainEventSubscriberPartitionStatus] =
    service.subscriberPartitionStatuses(
      subscriberId = subscriberId,
      asOf = asOf,
      lagOnly = lagOnly,
      blockedOnly = blockedOnly,
      partitionKey = partitionKey
    )

  def eventCascadeRecords(
      tables: OpsAnalyticsTables,
      query: EventCascadeRecordQuery
  ): Vector[EventCascadeRecord] =
    tables.listEventCascadeRecords()
      .filter(record => query.status.forall(_ == record.status))
      .filter(record => query.consumer.forall(_ == record.consumer))
      .filter(record => query.eventType.forall(_ == record.eventType))
      .filter(record => query.aggregateType.forall(_ == record.aggregateType))
      .filter(record => query.aggregateId.forall(_ == record.aggregateId))
      .sortBy(record => (record.occurredAt, record.id.value))
