package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsAcknowledgeDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxRecord] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecord] =
    IO {
      val module = context.support.opsAnalyticsModule
      val actor = context.support.principal(operatorId)
      val at = java.time.Instant.now()
      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val record = module.domainEventOutboxRepository.findById(recordId)
          .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
        require(
          Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
          s"Only DeadLetter or Quarantined outbox records can be acknowledged, but ${recordId.value} is ${record.status}"
        )

        val acknowledged = module.domainEventOutboxRepository.save(record.markCompleted(at))
        module.auditEventRepository.save(
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
    }
