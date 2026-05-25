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

final case class OpsAnalyticsAcknowledgeDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    recordIds: Vector[DomainEventOutboxRecordId],
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxBatchOperationResultResponse] derives ReadWriter:

  require(recordIds.nonEmpty, "Batch acknowledge requires at least one recordId")

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxBatchOperationResultResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      acknowledgedAt <- IO.realTimeInstant
      command = AcknowledgeOutboxBatchCommand(actor, recordIds.distinct, acknowledgedAt, note)
      result <- IO(acknowledgeOutboxBatch(context, command))
    yield DomainEventOutboxBatchOperationResultResponse.fromDomain(result)

  private def acknowledgeOutboxBatch(
      context: ApiPlanContext,
      command: AcknowledgeOutboxBatchCommand
  ): DomainEventOutboxBatchOperationResult =
    val (succeededIds, failures) = command.recordIds.foldLeft(
      Vector.empty[DomainEventOutboxRecordId] -> Vector.empty[DomainEventOutboxOperationFailure]
    ) { case ((succeeded, failed), currentRecordId) =>
      acknowledgeOutboxRecordResult(context, currentRecordId, command.actor, command.acknowledgedAt) match
        case Right(_)    => (succeeded :+ currentRecordId) -> failed
        case Left(error) => succeeded -> (failed :+ error)
    }

    DomainEventOutboxBatchOperationResult(
      action = "ack",
      processedAt = command.acknowledgedAt,
      requestedCount = command.recordIds.size,
      succeededRecordIds = succeededIds,
      failures = failures
    )

  private def acknowledgeOutboxRecordResult(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      at: Instant
  ): Either[DomainEventOutboxOperationFailure, DomainEventOutboxRecord] =
    try Right(acknowledgeOutboxRecord(context, currentRecordId, actor, at))
    catch
      case error: IllegalArgumentException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: NoSuchElementException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: IllegalStateException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))

  private def acknowledgeOutboxRecord(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      at: Instant
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.acknowledge(context, currentRecordId, actor, at, note)

  private final case class AcknowledgeOutboxBatchCommand(
      actor: AccessPrincipal,
      recordIds: Vector[DomainEventOutboxRecordId],
      acknowledgedAt: Instant,
      note: Option[String]
  )
