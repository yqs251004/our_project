package events

type OutboxBackedDomainEventBus = riichinexus.infrastructure.events.OutboxBackedDomainEventBus

object OutboxBackedDomainEventBus:
  def apply(
      outboxRepository: ports.DomainEventOutboxRepository,
      deliveryReceiptRepository: ports.DomainEventDeliveryReceiptRepository,
      subscriberCursorRepository: ports.DomainEventSubscriberCursorRepository,
      transactionManager: ports.TransactionManager,
      initialSubscribers: Vector[ports.DomainEventSubscriber] = Vector.empty,
      retryDelay: java.time.Duration = java.time.Duration.ofSeconds(30),
      deferredRedeliveryDelay: java.time.Duration = java.time.Duration.ofSeconds(2),
      maxAttempts: Int = 5,
      pollInterval: java.time.Duration = java.time.Duration.ofSeconds(2)
  ): OutboxBackedDomainEventBus =
    new riichinexus.infrastructure.events.OutboxBackedDomainEventBus(
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
