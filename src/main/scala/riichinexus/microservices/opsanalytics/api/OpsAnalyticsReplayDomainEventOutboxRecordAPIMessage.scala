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

final case class OpsAnalyticsReplayDomainEventOutboxRecordAPIMessage(
    recordId: DomainEventOutboxRecordId,
    operatorId: PlayerId,
    replayAt: Option[Instant] = None,
    note: Option[String] = None
) extends APIMessage[DomainEventOutboxRecordResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventOutboxRecordResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      replayAtInstant <- resolveReplayAt
      replayedAt <- IO.realTimeInstant
      command = ReplayOutboxRecordCommand(recordId, actor, replayAtInstant, replayedAt, note)
      record <- IO(replayOutboxRecord(context, command))
    yield DomainEventOutboxRecordResponse.fromDomain(record)

  private def resolveReplayAt: IO[Instant] =
    replayAt match
      case Some(value) => IO(value)
      case None        => IO.realTimeInstant

  private def replayOutboxRecord(
      context: ApiPlanContext,
      command: ReplayOutboxRecordCommand
  ): DomainEventOutboxRecord =
    OpsAnalyticsDomainEventOutboxOperations.replay(
      context,
      command.recordId,
      command.actor,
      command.replayAt,
      command.replayedAt,
      command.note
    )

  private final case class ReplayOutboxRecordCommand(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAt: Instant,
      replayedAt: Instant,
      note: Option[String]
  )
