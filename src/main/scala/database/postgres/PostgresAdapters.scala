package database.postgres

type PostgresSchemaInitializer = riichinexus.infrastructure.postgres.PostgresSchemaInitializer
type PostgresPlayerRepository = riichinexus.infrastructure.postgres.PostgresPlayerRepository
type PostgresClubRepository = riichinexus.infrastructure.postgres.PostgresClubRepository
type PostgresTournamentRepository = riichinexus.infrastructure.postgres.PostgresTournamentRepository
type PostgresTableRepository = riichinexus.infrastructure.postgres.PostgresTableRepository
type PostgresAppealTicketRepository = riichinexus.infrastructure.postgres.PostgresAppealTicketRepository
type PostgresDashboardRepository = riichinexus.infrastructure.postgres.PostgresDashboardRepository
type PostgresAdvancedStatsBoardRepository = riichinexus.infrastructure.postgres.PostgresAdvancedStatsBoardRepository
type PostgresAdvancedStatsRecomputeTaskRepository = riichinexus.infrastructure.postgres.PostgresAdvancedStatsRecomputeTaskRepository
type PostgresDomainEventOutboxRepository = riichinexus.infrastructure.postgres.PostgresDomainEventOutboxRepository
type PostgresDomainEventDeliveryReceiptRepository = riichinexus.infrastructure.postgres.PostgresDomainEventDeliveryReceiptRepository
type PostgresDomainEventSubscriberCursorRepository = riichinexus.infrastructure.postgres.PostgresDomainEventSubscriberCursorRepository

object PostgresSchemaInitializer:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresSchemaInitializer =
    new riichinexus.infrastructure.postgres.PostgresSchemaInitializer(connectionFactory)

object PostgresPlayerRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPlayerRepository =
    new riichinexus.infrastructure.postgres.PostgresPlayerRepository(connectionFactory)

object PostgresClubRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresClubRepository =
    new riichinexus.infrastructure.postgres.PostgresClubRepository(connectionFactory)

object PostgresTournamentRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentRepository =
    new riichinexus.infrastructure.postgres.PostgresTournamentRepository(connectionFactory)

object PostgresTableRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTableRepository =
    new riichinexus.infrastructure.postgres.PostgresTableRepository(connectionFactory)

object PostgresAppealTicketRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAppealTicketRepository =
    new riichinexus.infrastructure.postgres.PostgresAppealTicketRepository(connectionFactory)

object PostgresDashboardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDashboardRepository =
    new riichinexus.infrastructure.postgres.PostgresDashboardRepository(connectionFactory)

object PostgresAdvancedStatsBoardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdvancedStatsBoardRepository =
    new riichinexus.infrastructure.postgres.PostgresAdvancedStatsBoardRepository(connectionFactory)

object PostgresAdvancedStatsRecomputeTaskRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdvancedStatsRecomputeTaskRepository =
    new riichinexus.infrastructure.postgres.PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory)

object PostgresDomainEventOutboxRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventOutboxRepository =
    new riichinexus.infrastructure.postgres.PostgresDomainEventOutboxRepository(connectionFactory)

object PostgresDomainEventDeliveryReceiptRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventDeliveryReceiptRepository =
    new riichinexus.infrastructure.postgres.PostgresDomainEventDeliveryReceiptRepository(connectionFactory)

object PostgresDomainEventSubscriberCursorRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventSubscriberCursorRepository =
    new riichinexus.infrastructure.postgres.PostgresDomainEventSubscriberCursorRepository(connectionFactory)
