package riichinexus.microservices.opsanalytics.api

import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventOutboxOperations

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventOutboxBatchOperationResult as DomainEventOutboxBatchOperationResultResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import upickle.default.*

final case class OpsAnalyticsQuarantineDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    recordIds: Vector[DomainEventOutboxRecordId],
    reason: String
) extends APIMessage[DomainEventOutboxBatchOperationResultResponse] derives ReadWriter:

  require(recordIds.nonEmpty, "Batch quarantine requires at least one recordId")

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxBatchOperationResultResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      quarantinedAt <- IO.realTimeInstant
      command <- IO(resolveCommand(actor, quarantinedAt))
      result <- IO(quarantineOutboxBatch(context, command))
    yield DomainEventOutboxBatchOperationResultResponse.fromDomain(result)

  private def resolveCommand(actor: AccessPrincipal, quarantinedAt: Instant): QuarantineOutboxBatchCommand =
    val normalizedReason = reason.trim
    require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
    QuarantineOutboxBatchCommand(actor, recordIds.distinct, normalizedReason, quarantinedAt)

  private def quarantineOutboxBatch(
      context: ApiPlanContext,
      command: QuarantineOutboxBatchCommand
  ): DomainEventOutboxBatchOperationResult =
    val (succeededIds, failures) = command.recordIds.foldLeft(
      Vector.empty[DomainEventOutboxRecordId] -> Vector.empty[DomainEventOutboxOperationFailure]
    ) { case ((succeeded, failed), currentRecordId) =>
      quarantineOutboxRecordResult(context, currentRecordId, command.actor, command.reason, command.quarantinedAt) match
        case Right(_)    => (succeeded :+ currentRecordId) -> failed
        case Left(error) => succeeded -> (failed :+ error)
    }

    DomainEventOutboxBatchOperationResult(
      action = "quarantine",
      processedAt = command.quarantinedAt,
      requestedCount = command.recordIds.size,
      succeededRecordIds = succeededIds,
      failures = failures
    )

  private def quarantineOutboxRecordResult(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      normalizedReason: String,
      at: Instant
  ): Either[DomainEventOutboxOperationFailure, DomainEventOutboxRecord] =
    try Right(quarantineOutboxRecord(context, currentRecordId, actor, normalizedReason, at))
    catch
      case error: IllegalArgumentException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: NoSuchElementException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: IllegalStateException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))

  private def quarantineOutboxRecord(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      normalizedReason: String,
      at: Instant
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.quarantine(context, currentRecordId, actor, normalizedReason, at)

  private final case class QuarantineOutboxBatchCommand(
      actor: AccessPrincipal,
      recordIds: Vector[DomainEventOutboxRecordId],
      reason: String,
      quarantinedAt: Instant
  )
