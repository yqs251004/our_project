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

final case class OpsAnalyticsQuarantineDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[DomainEventOutboxRecordResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecordResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      quarantinedAt <- IO.realTimeInstant
      command <- IO(resolveCommand(actor, quarantinedAt))
      record <- IO(quarantineOutboxRecord(context, command))
    yield DomainEventOutboxRecordResponse.fromDomain(record)

  private def resolveCommand(actor: AccessPrincipal, quarantinedAt: Instant): QuarantineOutboxRecordCommand =
    val normalizedReason = reason.trim
    require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
    QuarantineOutboxRecordCommand(recordId, actor, normalizedReason, quarantinedAt)

  private def quarantineOutboxRecord(
      context: ApiPlanContext,
      command: QuarantineOutboxRecordCommand
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.quarantine(
      context,
      command.recordId,
      command.actor,
      command.reason,
      command.quarantinedAt
    )

  private final case class QuarantineOutboxRecordCommand(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      reason: String,
      quarantinedAt: Instant
  )
