package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsQuarantineDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    recordIds: Vector[DomainEventOutboxRecordId],
    reason: String
) extends APIMessage[DomainEventOutboxBatchOperationResult] derives ReadWriter:

  require(recordIds.nonEmpty, "Batch quarantine requires at least one recordId")

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxBatchOperationResult] =
    IO {
      val actor = context.support.principal(operatorId)
      val at = java.time.Instant.now()
      val normalizedReason = reason.trim
      require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
      val normalizedIds = recordIds.distinct
      val failures = Vector.newBuilder[DomainEventOutboxOperationFailure]
      val succeededIds = Vector.newBuilder[DomainEventOutboxRecordId]

      normalizedIds.foreach { currentRecordId =>
        try
          quarantineOutboxRecord(context, currentRecordId, actor, normalizedReason, at)
          succeededIds += currentRecordId
        catch
          case error: IllegalArgumentException =>
            failures += DomainEventOutboxOperationFailure(currentRecordId, error.getMessage)
          case error: NoSuchElementException =>
            failures += DomainEventOutboxOperationFailure(currentRecordId, error.getMessage)
          case error: IllegalStateException =>
            failures += DomainEventOutboxOperationFailure(currentRecordId, error.getMessage)
      }

      DomainEventOutboxBatchOperationResult(
        action = "quarantine",
        processedAt = at,
        requestedCount = normalizedIds.size,
        succeededRecordIds = succeededIds.result(),
        failures = failures.result()
      )
    }

  private def quarantineOutboxRecord(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      normalizedReason: String,
      at: java.time.Instant
  ): DomainEventOutboxRecord =
    val module = context.support.opsAnalyticsModule
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = module.domainEventOutboxRepository.findById(currentRecordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${currentRecordId.value} was not found"))
      require(
        record.status != DomainEventOutboxStatus.Completed,
        s"Completed outbox record ${currentRecordId.value} cannot be quarantined"
      )
      require(
        record.status != DomainEventOutboxStatus.Quarantined,
        s"Outbox record ${currentRecordId.value} is already quarantined"
      )

      val quarantined = module.domainEventOutboxRepository.save(record.markQuarantined(normalizedReason, at))
      module.auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = currentRecordId.value,
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
