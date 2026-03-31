package ports

type PlayerRepository = riichinexus.application.ports.PlayerRepository
type GuestSessionRepository = riichinexus.application.ports.GuestSessionRepository
type ClubRepository = riichinexus.application.ports.ClubRepository
type TournamentRepository = riichinexus.application.ports.TournamentRepository
type TableRepository = riichinexus.application.ports.TableRepository
type MatchRecordRepository = riichinexus.application.ports.MatchRecordRepository
type PaifuRepository = riichinexus.application.ports.PaifuRepository
type AppealTicketRepository = riichinexus.application.ports.AppealTicketRepository
type DashboardRepository = riichinexus.application.ports.DashboardRepository
type AdvancedStatsBoardRepository = riichinexus.application.ports.AdvancedStatsBoardRepository
type AdvancedStatsRecomputeTaskRepository = riichinexus.application.ports.AdvancedStatsRecomputeTaskRepository
type GlobalDictionaryRepository = riichinexus.application.ports.GlobalDictionaryRepository
type DictionaryNamespaceRepository = riichinexus.application.ports.DictionaryNamespaceRepository
type TournamentSettlementRepository = riichinexus.application.ports.TournamentSettlementRepository
type EventCascadeRecordRepository = riichinexus.application.ports.EventCascadeRecordRepository
type DomainEventOutboxRepository = riichinexus.application.ports.DomainEventOutboxRepository
type DomainEventDeliveryReceiptRepository = riichinexus.application.ports.DomainEventDeliveryReceiptRepository
type DomainEventSubscriberCursorRepository = riichinexus.application.ports.DomainEventSubscriberCursorRepository
type AuditEventRepository = riichinexus.application.ports.AuditEventRepository

type DomainEventSubscriberPartitionStrategy = riichinexus.application.ports.DomainEventSubscriberPartitionStrategy
type DomainEventSubscriber = riichinexus.application.ports.DomainEventSubscriber
type DomainEventBus = riichinexus.application.ports.DomainEventBus

type OptimisticConcurrencyException = riichinexus.application.ports.OptimisticConcurrencyException
type TransactionManager = riichinexus.application.ports.TransactionManager

val NoOpTransactionManager: TransactionManager =
  riichinexus.application.ports.NoOpTransactionManager

object OptimisticConcurrencyException:
  def apply(
      aggregateType: String,
      aggregateId: String,
      expectedVersion: Int,
      actualVersion: Option[Int]
  ): OptimisticConcurrencyException =
    riichinexus.application.ports.OptimisticConcurrencyException(
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      expectedVersion = expectedVersion,
      actualVersion = actualVersion
    )

object DomainEventSubscriberPartitionStrategy:
  val Global: DomainEventSubscriberPartitionStrategy =
    riichinexus.application.ports.DomainEventSubscriberPartitionStrategy.Global
  val EventType: DomainEventSubscriberPartitionStrategy =
    riichinexus.application.ports.DomainEventSubscriberPartitionStrategy.EventType
  val AggregateRoot: DomainEventSubscriberPartitionStrategy =
    riichinexus.application.ports.DomainEventSubscriberPartitionStrategy.AggregateRoot
