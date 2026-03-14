package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.application.service.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.memory.*
import riichinexus.infrastructure.postgres.*

final case class ApplicationContext(
    playerService: PlayerApplicationService,
    publicQueryService: PublicQueryService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    appealService: AppealApplicationService,
    superAdminService: SuperAdminService,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    appealTicketRepository: AppealTicketRepository,
    dashboardRepository: DashboardRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
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
    val authorizationService = StrictRbacAuthorizationService()
    val playerRepository = InMemoryPlayerRepository()
    val clubRepository = InMemoryClubRepository()
    val tournamentRepository = InMemoryTournamentRepository()
    val tableRepository = InMemoryTableRepository()
    val matchRecordRepository = InMemoryMatchRecordRepository()
    val paifuRepository = InMemoryPaifuRepository()
    val appealTicketRepository = InMemoryAppealTicketRepository()
    val dashboardRepository = InMemoryDashboardRepository()
    val globalDictionaryRepository = InMemoryGlobalDictionaryRepository()

    val eventBus = InMemoryDomainEventBus()
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      tournamentRepository,
      playerRepository,
      clubRepository,
      tableRepository,
      matchRecordRepository,
      tournamentRuleEngine,
      transactionManager
    )
    eventBus.register(
      RatingProjectionSubscriber(playerRepository, PairwiseEloRatingService())
    )
    eventBus.register(ClubProjectionSubscriber(clubRepository, playerRepository))
    eventBus.register(
      DashboardProjectionSubscriber(
        matchRecordRepository,
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(playerRepository, transactionManager),
      publicQueryService = PublicQueryService(
        tournamentRepository,
        tableRepository,
        playerRepository,
        clubRepository,
        authorizationService
      ),
      clubService = ClubApplicationService(
        clubRepository,
        playerRepository,
        transactionManager,
        authorizationService
      ),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        tableRepository,
        matchRecordRepository,
        BalancedEloSeatingPolicy(),
        tournamentRuleEngine,
        knockoutStageCoordinator,
        transactionManager,
        authorizationService
      ),
      tableService = TableLifecycleService(
        tableRepository,
        paifuRepository,
        matchRecordRepository,
        knockoutStageCoordinator,
        eventBus,
        transactionManager,
        authorizationService
      ),
      appealService = AppealApplicationService(
        appealTicketRepository,
        tableRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      superAdminService = SuperAdminService(
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      paifuRepository = paifuRepository,
      appealTicketRepository = appealTicketRepository,
      dashboardRepository = dashboardRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      eventBus = eventBus
    )

  def postgres(config: DatabaseConfig): ApplicationContext =
    val connectionFactory = JdbcConnectionFactory(config)
    PostgresSchemaInitializer(connectionFactory).initialize()
    val transactionManager = JdbcTransactionManager(connectionFactory)
    val authorizationService = StrictRbacAuthorizationService()

    val playerRepository = PostgresPlayerRepository(connectionFactory)
    val clubRepository = PostgresClubRepository(connectionFactory)
    val tournamentRepository = PostgresTournamentRepository(connectionFactory)
    val tableRepository = PostgresTableRepository(connectionFactory)
    val matchRecordRepository = PostgresMatchRecordRepository(connectionFactory)
    val paifuRepository = PostgresPaifuRepository(connectionFactory)
    val appealTicketRepository = PostgresAppealTicketRepository(connectionFactory)
    val dashboardRepository = PostgresDashboardRepository(connectionFactory)
    val globalDictionaryRepository = PostgresGlobalDictionaryRepository(connectionFactory)

    val eventBus = InMemoryDomainEventBus()
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      tournamentRepository,
      playerRepository,
      clubRepository,
      tableRepository,
      matchRecordRepository,
      tournamentRuleEngine,
      transactionManager
    )
    eventBus.register(
      RatingProjectionSubscriber(playerRepository, PairwiseEloRatingService())
    )
    eventBus.register(ClubProjectionSubscriber(clubRepository, playerRepository))
    eventBus.register(
      DashboardProjectionSubscriber(
        matchRecordRepository,
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(playerRepository, transactionManager),
      publicQueryService = PublicQueryService(
        tournamentRepository,
        tableRepository,
        playerRepository,
        clubRepository,
        authorizationService
      ),
      clubService = ClubApplicationService(
        clubRepository,
        playerRepository,
        transactionManager,
        authorizationService
      ),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        tableRepository,
        matchRecordRepository,
        BalancedEloSeatingPolicy(),
        tournamentRuleEngine,
        knockoutStageCoordinator,
        transactionManager,
        authorizationService
      ),
      tableService = TableLifecycleService(
        tableRepository,
        paifuRepository,
        matchRecordRepository,
        knockoutStageCoordinator,
        eventBus,
        transactionManager,
        authorizationService
      ),
      appealService = AppealApplicationService(
        appealTicketRepository,
        tableRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      superAdminService = SuperAdminService(
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      paifuRepository = paifuRepository,
      appealTicketRepository = appealTicketRepository,
      dashboardRepository = dashboardRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      eventBus = eventBus
    )
