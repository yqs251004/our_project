package riichinexus.microservices.opsanalytics.api

import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventOutboxOperations

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventOutboxRecord as DomainEventOutboxRecordResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsAcknowledgeDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxRecordResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecordResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      acknowledgedAt <- IO.realTimeInstant
      command = AcknowledgeOutboxRecordCommand(recordId, actor, acknowledgedAt, note)
      record <- IO(acknowledgeOutboxRecord(context, command))
    yield DomainEventOutboxRecordResponse.fromDomain(record)

  private def acknowledgeOutboxRecord(
      context: ApiPlanContext,
      command: AcknowledgeOutboxRecordCommand
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.acknowledge(
      context,
      command.recordId,
      command.actor,
      command.acknowledgedAt,
      command.note
    )

  private final case class AcknowledgeOutboxRecordCommand(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      acknowledgedAt: Instant,
      note: Option[String]
  )
