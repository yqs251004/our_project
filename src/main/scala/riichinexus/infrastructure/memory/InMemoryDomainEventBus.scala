package riichinexus.infrastructure.memory

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.DomainEvent

final class InMemoryDomainEventBus(
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
) extends DomainEventBus:
  private val subscribers = mutable.ArrayBuffer.from(initialSubscribers)

  override def publish(event: DomainEvent): Unit =
    subscribers.foreach(_.handle(event))

  override def register(subscriber: DomainEventSubscriber): Unit =
    subscribers += subscriber

object InMemoryDomainEventBus:
  def apply(
      initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
  ): InMemoryDomainEventBus =
    new InMemoryDomainEventBus(initialSubscribers)
