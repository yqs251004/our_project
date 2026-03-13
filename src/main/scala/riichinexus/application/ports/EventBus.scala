package riichinexus.application.ports

import riichinexus.domain.event.DomainEvent

trait DomainEventSubscriber:
  def handle(event: DomainEvent): Unit

trait DomainEventBus:
  def publish(event: DomainEvent): Unit
  def register(subscriber: DomainEventSubscriber): Unit
