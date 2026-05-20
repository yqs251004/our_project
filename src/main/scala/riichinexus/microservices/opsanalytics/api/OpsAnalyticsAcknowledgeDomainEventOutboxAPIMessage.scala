package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsAcknowledgeDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    recordIds: Vector[DomainEventOutboxRecordId],
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxBatchOperationResult] derives ReadWriter:

  require(recordIds.nonEmpty, "Batch acknowledge requires at least one recordId")

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxBatchOperationResult] =
    IO {
      val actor = context.support.principal(operatorId)
      val at = java.time.Instant.now()
      val normalizedIds = recordIds.distinct
      val failures = Vector.newBuilder[DomainEventOutboxOperationFailure]
      val succeededIds = Vector.newBuilder[DomainEventOutboxRecordId]

      normalizedIds.foreach { currentRecordId =>
        try
          acknowledgeOutboxRecord(context, currentRecordId, actor, at)
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
        action = "ack",
        processedAt = at,
        requestedCount = normalizedIds.size,
        succeededRecordIds = succeededIds.result(),
        failures = failures.result()
      )
    }

  private def acknowledgeOutboxRecord(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      at: java.time.Instant
  ): DomainEventOutboxRecord =
    val module = context.support.opsAnalyticsModule
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = module.domainEventOutboxRepository.findById(currentRecordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${currentRecordId.value} was not found"))
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be acknowledged, but ${currentRecordId.value} is ${record.status}"
      )

      val acknowledged = module.domainEventOutboxRepository.save(record.markCompleted(at))
      module.auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = currentRecordId.value,
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
