package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.application.service.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.memory.*
import riichinexus.infrastructure.postgres.*

final case class ApplicationContext(
    playerService: PlayerApplicationService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    dashboardRepository: DashboardRepository,
    eventBus: DomainEventBus
)

object ApplicationContext:
  def fromEnvironment(env: collection.Map[String, String] = sys.env): ApplicationContext =
    val storageMode = env.get("RIICHI_STORAGE").map(_.trim.toLowerCase)
    if storageMode.contains("postgres") || env.contains("RIICHI_DB_URL") then
      postgres(DatabaseConfig.fromEnv(env))
    else inMemory()

  def inMemory(): ApplicationContext =
    val transactionManager = NoOpTransactionManager
    val playerRepository = InMemoryPlayerRepository()
    val clubRepository = InMemoryClubRepository()
    val tournamentRepository = InMemoryTournamentRepository()
    val tableRepository = InMemoryTableRepository()
    val paifuRepository = InMemoryPaifuRepository()
    val dashboardRepository = InMemoryDashboardRepository()

    val eventBus = InMemoryDomainEventBus()
    eventBus.register(
      RatingProjectionSubscriber(playerRepository, PairwiseEloRatingService())
    )
    eventBus.register(ClubProjectionSubscriber(clubRepository, playerRepository))
    eventBus.register(
      DashboardProjectionSubscriber(
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(playerRepository, transactionManager),
      clubService = ClubApplicationService(clubRepository, playerRepository, transactionManager),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        tableRepository,
        BalancedEloSeatingPolicy(),
        transactionManager
      ),
      tableService = TableLifecycleService(tableRepository, paifuRepository, eventBus, transactionManager),
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      paifuRepository = paifuRepository,
      dashboardRepository = dashboardRepository,
      eventBus = eventBus
    )

  def postgres(config: DatabaseConfig): ApplicationContext =
    val connectionFactory = JdbcConnectionFactory(config)
    PostgresSchemaInitializer(connectionFactory).initialize()
    val transactionManager = JdbcTransactionManager(connectionFactory)

    val playerRepository = PostgresPlayerRepository(connectionFactory)
    val clubRepository = PostgresClubRepository(connectionFactory)
    val tournamentRepository = PostgresTournamentRepository(connectionFactory)
    val tableRepository = PostgresTableRepository(connectionFactory)
    val paifuRepository = PostgresPaifuRepository(connectionFactory)
    val dashboardRepository = PostgresDashboardRepository(connectionFactory)

    val eventBus = InMemoryDomainEventBus()
    eventBus.register(
      RatingProjectionSubscriber(playerRepository, PairwiseEloRatingService())
    )
    eventBus.register(ClubProjectionSubscriber(clubRepository, playerRepository))
    eventBus.register(
      DashboardProjectionSubscriber(
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(playerRepository, transactionManager),
      clubService = ClubApplicationService(clubRepository, playerRepository, transactionManager),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        tableRepository,
        BalancedEloSeatingPolicy(),
        transactionManager
      ),
      tableService = TableLifecycleService(tableRepository, paifuRepository, eventBus, transactionManager),
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      paifuRepository = paifuRepository,
      dashboardRepository = dashboardRepository,
      eventBus = eventBus
    )
