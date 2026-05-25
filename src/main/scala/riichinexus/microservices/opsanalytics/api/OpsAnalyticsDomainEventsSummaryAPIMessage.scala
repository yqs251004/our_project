package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventBusSummary as DomainEventBusSummaryResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventSubscriberQueries
import upickle.default.*

final case class OpsAnalyticsDomainEventsSummaryAPIMessage(
    operatorId: PlayerId,
    asOf: Option[Instant] = None
) extends APIMessage[DomainEventBusSummaryResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DomainEventBusSummaryResponse] =
    for
      _ <- IO(requireOpsAdmin(context))
      module = context.support.opsAnalyticsModule
      resolvedAsOf <- resolveAsOf
      summary <- IO(buildSummary(module, resolvedAsOf))
    yield DomainEventBusSummaryResponse.fromDomain(summary)

  private def resolveAsOf: IO[Instant] =
    asOf match
      case Some(value) => IO(value)
      case None        => IO.realTimeInstant

  private def buildSummary(
      module: OpsAnalyticsModuleContext,
      resolvedAsOf: Instant
  ): DomainEventBusSummary =
    val records = module.domainEventOutboxRepository.findAll().sortBy(_.sequenceNo)
    val blockedSubscriberCount = module.domainEventSubscribers.count { subscriber =>
      OpsAnalyticsDomainEventSubscriberQueries.subscriberHasBlockedPartition(module, subscriber, resolvedAsOf)
    }

    DomainEventBusSummary(
      asOf = resolvedAsOf,
      registeredSubscriberCount = module.domainEventSubscribers.size,
      cursorCount = module.domainEventSubscriberCursorRepository.findAll().size,
      pendingCount = records.count(_.status == DomainEventOutboxStatus.Pending),
      scheduledPendingCount = records.count(record =>
        record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(resolvedAsOf)
      ),
      processingCount = records.count(_.status == DomainEventOutboxStatus.Processing),
      completedCount = records.count(_.status == DomainEventOutboxStatus.Completed),
      deadLetterCount = records.count(_.status == DomainEventOutboxStatus.DeadLetter),
      quarantinedCount = records.count(_.status == DomainEventOutboxStatus.Quarantined),
      highestAssignedSequenceNo = records.lastOption.map(_.sequenceNo),
      nextRunnableSequenceNo = records.find(_.isRunnable(resolvedAsOf)).map(_.sequenceNo),
      oldestPendingOccurredAt = records.find(_.status == DomainEventOutboxStatus.Pending).map(_.occurredAt),
      oldestDeadLetterOccurredAt = records.find(_.status == DomainEventOutboxStatus.DeadLetter).map(_.occurredAt),
      oldestQuarantinedOccurredAt = records.find(_.status == DomainEventOutboxStatus.Quarantined).map(_.occurredAt),
      blockedSubscriberCount = blockedSubscriberCount
    )

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator
