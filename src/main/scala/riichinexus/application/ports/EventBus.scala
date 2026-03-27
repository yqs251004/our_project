package riichinexus.application.ports

import java.time.Instant

import riichinexus.domain.event.DomainEvent
import riichinexus.domain.model.DomainEventOutboxRecord

enum DomainEventSubscriberPartitionStrategy derives CanEqual:
  case Global
  case EventType
  case AggregateRoot

  def partitionKey(record: DomainEventOutboxRecord): String =
    this match
      case Global        => "global"
      case EventType     => s"event-type:${record.eventType}"
      case AggregateRoot => s"aggregate:${record.aggregateType}:${record.aggregateId}"

trait DomainEventSubscriber:
  def subscriberId: String =
    getClass.getName.stripSuffix("$")

  def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.Global

  def handle(event: DomainEvent): Unit

trait DomainEventBus:
  def publish(event: DomainEvent): Unit
  def register(subscriber: DomainEventSubscriber): Unit
  def drainPendingNow(limit: Int = 100, processedAt: Instant = Instant.now()): Int = 0
