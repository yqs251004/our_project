package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsQuarantineDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[DomainEventOutboxRecord] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecord] =
    IO {
      val module = context.support.opsAnalyticsModule
      val actor = context.support.principal(operatorId)
      val at = java.time.Instant.now()
      val normalizedReason = reason.trim
      require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val record = module.domainEventOutboxRepository.findById(recordId)
          .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
        require(
          record.status != DomainEventOutboxStatus.Completed,
          s"Completed outbox record ${recordId.value} cannot be quarantined"
        )
        require(
          record.status != DomainEventOutboxStatus.Quarantined,
          s"Outbox record ${recordId.value} is already quarantined"
        )

        val quarantined = module.domainEventOutboxRepository.save(record.markQuarantined(normalizedReason, at))
        module.auditEventRepository.save(
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
    }
