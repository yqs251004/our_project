package riichinexus.microservices.opsanalytics.api

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.domain.event.*

final class AdvancedStatsProjectionSubscriber(
    advancedStatsPipelineService: AdvancedStatsPipelineService
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        advancedStatsPipelineService.enqueueImpactedOwners(matchRecord, occurredAt)
        ()
      case _ =>
        ()
