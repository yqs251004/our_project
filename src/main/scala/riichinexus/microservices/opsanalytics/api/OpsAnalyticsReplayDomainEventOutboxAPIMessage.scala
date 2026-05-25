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

final case class OpsAnalyticsReplayDomainEventOutboxAPIMessage(
    operatorId: PlayerId,
    recordIds: Vector[DomainEventOutboxRecordId],
    replayAt: Option[Instant] = None,
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxBatchOperationResultResponse] derives ReadWriter:

  require(recordIds.nonEmpty, "Batch replay requires at least one recordId")

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxBatchOperationResultResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      replayAtInstant <- resolveReplayAt
      replayedAt <- IO.realTimeInstant
      command = ReplayOutboxBatchCommand(actor, recordIds.distinct, replayAtInstant, replayedAt, note)
      result <- IO(replayOutboxBatch(context, command))
    yield DomainEventOutboxBatchOperationResultResponse.fromDomain(result)

  private def resolveReplayAt: IO[Instant] =
    replayAt match
      case Some(value) => IO(value)
      case None        => IO.realTimeInstant

  private def replayOutboxBatch(
      context: ApiPlanContext,
      command: ReplayOutboxBatchCommand
  ): DomainEventOutboxBatchOperationResult =
    val (succeededIds, failures) = command.recordIds.foldLeft(
      Vector.empty[DomainEventOutboxRecordId] -> Vector.empty[DomainEventOutboxOperationFailure]
    ) { case ((succeeded, failed), currentRecordId) =>
      replayOutboxRecordResult(context, currentRecordId, command.actor, command.replayAt, command.replayedAt) match
        case Right(_)    => (succeeded :+ currentRecordId) -> failed
        case Left(error) => succeeded -> (failed :+ error)
    }

    DomainEventOutboxBatchOperationResult(
      action = "replay",
      processedAt = command.replayedAt,
      requestedCount = command.recordIds.size,
      succeededRecordIds = succeededIds,
      failures = failures
    )

  private def replayOutboxRecordResult(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAtInstant: Instant,
      at: Instant
  ): Either[DomainEventOutboxOperationFailure, DomainEventOutboxRecord] =
    try Right(replayOutboxRecord(context, currentRecordId, actor, replayAtInstant, at))
    catch
      case error: IllegalArgumentException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: NoSuchElementException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))
      case error: IllegalStateException =>
        Left(DomainEventOutboxOperationFailure(currentRecordId, error.getMessage))

  private def replayOutboxRecord(
      context: ApiPlanContext,
      currentRecordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAtInstant: Instant,
      at: Instant
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.replay(context, currentRecordId, actor, replayAtInstant, at, note)

  private final case class ReplayOutboxBatchCommand(
      actor: AccessPrincipal,
      recordIds: Vector[DomainEventOutboxRecordId],
      replayAt: Instant,
      replayedAt: Instant,
      note: Option[String]
  )
