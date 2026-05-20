package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsReplayDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    replayAt: Option[Instant] = None,
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxRecord] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecord] =
    IO {
      val module = context.support.opsAnalyticsModule
      val actor = context.support.principal(operatorId)
      val replayAtInstant = replayAt.getOrElse(Instant.now())
      val at = Instant.now()
      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val record = module.domainEventOutboxRepository.findById(recordId)
          .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
        require(
          Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
          s"Only DeadLetter or Quarantined outbox records can be replayed, but ${recordId.value} is ${record.status}"
        )

        val replayed = module.domainEventOutboxRepository.save(record.markReplayed(replayAtInstant))
        module.auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "domain-event-outbox-record",
            aggregateId = recordId.value,
            eventType = "DomainEventOutboxReplayed",
            occurredAt = at,
            actorId = actor.playerId,
            details = Map(
              "priorStatus" -> record.status.toString,
              "replayAt" -> replayAtInstant.toString,
              "eventType" -> record.eventType,
              "aggregateType" -> record.aggregateType,
              "aggregateId" -> record.aggregateId
            ),
            note = note
          )
        )
        replayed
      }
    }
