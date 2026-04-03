package riichinexus.infrastructure.events

type OutboxBackedDomainEventBus = _root_.events.OutboxBackedDomainEventBus

object OutboxBackedDomainEventBus:
  def apply(
      outboxRepository: riichinexus.application.ports.DomainEventOutboxRepository,
      deliveryReceiptRepository: riichinexus.application.ports.DomainEventDeliveryReceiptRepository,
      subscriberCursorRepository: riichinexus.application.ports.DomainEventSubscriberCursorRepository,
      transactionManager: riichinexus.application.ports.TransactionManager,
      initialSubscribers: Vector[riichinexus.application.ports.DomainEventSubscriber] = Vector.empty,
      retryDelay: java.time.Duration = java.time.Duration.ofSeconds(30),
      deferredRedeliveryDelay: java.time.Duration = java.time.Duration.ofSeconds(2),
      maxAttempts: Int = 5,
      pollInterval: java.time.Duration = java.time.Duration.ofSeconds(2)
  ): OutboxBackedDomainEventBus =
    _root_.events.OutboxBackedDomainEventBus(
      outboxRepository = outboxRepository,
      deliveryReceiptRepository = deliveryReceiptRepository,
      subscriberCursorRepository = subscriberCursorRepository,
      transactionManager = transactionManager,
      initialSubscribers = initialSubscribers,
      retryDelay = retryDelay,
      deferredRedeliveryDelay = deferredRedeliveryDelay,
      maxAttempts = maxAttempts,
      pollInterval = pollInterval
    )
