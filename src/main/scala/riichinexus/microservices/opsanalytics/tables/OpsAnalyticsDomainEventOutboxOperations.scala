package riichinexus.microservices.opsanalytics.tables

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.ApiPlanContext
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.domain.model.*

private[opsanalytics] object OpsAnalyticsDomainEventOutboxOperations:
  def acknowledge(
      context: ApiPlanContext,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      at: Instant,
      note: Option[String]
  ): DomainEventOutboxRecord =
    val module = context.support.opsAnalyticsModule
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = requireOutboxRecord(context, recordId)
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be acknowledged, but ${recordId.value} is ${record.status}"
      )

      DomainChangeInterpreter
        .auditOnly(module.transactionManager, module.auditEventRepository)
        .commitWithinTransaction(
          DomainChange(
            aggregate = record.markCompleted(at),
            persist = module.domainEventOutboxRepository.save,
            auditEntries = _ =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "domain-event-outbox-record",
                  aggregateId = recordId.value,
                  eventType = "DomainEventOutboxAcknowledged",
                  occurredAt = at,
                  actorId = actor.playerId,
                  details = auditDetails(record),
                  note = note
                )
              )
          )
        )
    }

  def quarantine(
      context: ApiPlanContext,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      normalizedReason: String,
      at: Instant
  ): DomainEventOutboxRecord =
    val module = context.support.opsAnalyticsModule
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = requireOutboxRecord(context, recordId)
      require(
        record.status != DomainEventOutboxStatus.Completed,
        s"Completed outbox record ${recordId.value} cannot be quarantined"
      )
      require(
        record.status != DomainEventOutboxStatus.Quarantined,
        s"Outbox record ${recordId.value} is already quarantined"
      )

      DomainChangeInterpreter
        .auditOnly(module.transactionManager, module.auditEventRepository)
        .commitWithinTransaction(
          DomainChange(
            aggregate = record.markQuarantined(normalizedReason, at),
            persist = module.domainEventOutboxRepository.save,
            auditEntries = _ =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "domain-event-outbox-record",
                  aggregateId = recordId.value,
                  eventType = "DomainEventOutboxQuarantined",
                  occurredAt = at,
                  actorId = actor.playerId,
                  details = auditDetails(record),
                  note = Some(normalizedReason)
                )
              )
          )
        )
    }

  def replay(
      context: ApiPlanContext,
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAt: Instant,
      at: Instant,
      note: Option[String]
  ): DomainEventOutboxRecord =
    val module = context.support.opsAnalyticsModule
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = requireOutboxRecord(context, recordId)
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be replayed, but ${recordId.value} is ${record.status}"
      )

      DomainChangeInterpreter
        .auditOnly(module.transactionManager, module.auditEventRepository)
        .commitWithinTransaction(
          DomainChange(
            aggregate = record.markReplayed(replayAt),
            persist = module.domainEventOutboxRepository.save,
            auditEntries = _ =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "domain-event-outbox-record",
                  aggregateId = recordId.value,
                  eventType = "DomainEventOutboxReplayed",
                  occurredAt = at,
                  actorId = actor.playerId,
                  details = auditDetails(record).updated("replayAt", replayAt.toString),
                  note = note
                )
              )
          )
        )
    }

  private def requireOutboxRecord(
      context: ApiPlanContext,
      recordId: DomainEventOutboxRecordId
  ): DomainEventOutboxRecord =
    context.support.opsAnalyticsModule.domainEventOutboxRepository.findById(recordId)
      .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))

  private def auditDetails(record: DomainEventOutboxRecord): Map[String, String] =
    Map(
      "priorStatus" -> record.status.toString,
      "eventType" -> record.eventType,
      "aggregateType" -> record.aggregateType,
      "aggregateId" -> record.aggregateId
    )
