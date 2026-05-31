package riichinexus.infrastructure.memory

import riichinexus.application.ports.*
import riichinexus.application.ports.DomainEvent

final class InMemoryDomainEventBus(
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
) extends DomainEventBus:
  private val subscribers = InMemoryAppendOnlyStore[DomainEventSubscriber](initialSubscribers)

  override def publish(event: DomainEvent): Unit =
    subscribers.values.foreach(_.handle(event))

  override def register(subscriber: DomainEventSubscriber): Unit =
    subscribers.append(subscriber)

object InMemoryDomainEventBus:
  def apply(
      initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
  ): InMemoryDomainEventBus =
    new InMemoryDomainEventBus(initialSubscribers)
