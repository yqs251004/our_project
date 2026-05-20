package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventOutboxHistoryView, DomainEventResponses}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsDomainEventOutboxHistoryAPIMessage(
    operatorId: PlayerId,
    recordId: DomainEventOutboxRecordId
) extends APIMessage[DomainEventOutboxHistoryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxHistoryView] =
    IO {
      val module = context.support.opsAnalyticsModule
      val actor = context.support.principal(operatorId)
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = module.domainEventOutboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      DomainEventOutboxHistoryView(
        record = record,
        auditTrail = module.auditEventRepository
          .findByAggregate("domain-event-outbox-record", recordId.value)
          .sortBy(entry => (entry.occurredAt, entry.id.value)),
        deliveryReceipts = module.domainEventDeliveryReceiptRepository.findAll()
          .filter(_.outboxRecordId == recordId)
          .sortBy(receipt => (receipt.deliveredAt, receipt.subscriberId, receipt.id.value))
      )
    }
