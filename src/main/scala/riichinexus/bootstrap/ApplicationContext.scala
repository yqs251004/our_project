package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.application.service.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.events.*
import riichinexus.infrastructure.memory.*
import riichinexus.infrastructure.postgres.*

final case class ApplicationContext(
    playerService: PlayerApplicationService,
    guestSessionService: GuestSessionApplicationService,
    publicQueryService: PublicQueryService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    appealService: AppealApplicationService,
    superAdminService: SuperAdminService,
    advancedStatsPipelineService: AdvancedStatsPipelineService,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    appealTicketRepository: AppealTicketRepository,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    tournamentSettlementRepository: TournamentSettlementRepository,
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    domainEventOutboxRepository: DomainEventOutboxRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    authorizationService: AuthorizationService,
    guestSessionRepository: GuestSessionRepository
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
    val guestSessionRepository = InMemoryGuestSessionRepository()
    val clubRepository = InMemoryClubRepository()
    val tournamentRepository = InMemoryTournamentRepository()
    val tableRepository = InMemoryTableRepository()
    val matchRecordRepository = InMemoryMatchRecordRepository()
    val paifuRepository = InMemoryPaifuRepository()
    val appealTicketRepository = InMemoryAppealTicketRepository()
    val dashboardRepository = InMemoryDashboardRepository()
    val advancedStatsBoardRepository = InMemoryAdvancedStatsBoardRepository()
    val advancedStatsRecomputeTaskRepository = InMemoryAdvancedStatsRecomputeTaskRepository()
    val globalDictionaryRepository = InMemoryGlobalDictionaryRepository()
    val dictionaryNamespaceRepository = InMemoryDictionaryNamespaceRepository()
    val tournamentSettlementRepository = InMemoryTournamentSettlementRepository()
    val eventCascadeRecordRepository = InMemoryEventCascadeRecordRepository()
    val domainEventOutboxRepository = InMemoryDomainEventOutboxRepository()
    val auditEventRepository = InMemoryAuditEventRepository()

    val eventBus = OutboxBackedDomainEventBus(domainEventOutboxRepository, transactionManager)
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val advancedStatsPipelineService = AdvancedStatsPipelineService(
      paifuRepository,
      matchRecordRepository,
      playerRepository,
      clubRepository,
      advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository,
      transactionManager
    )
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
      RatingProjectionSubscriber(
        playerRepository,
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(globalDictionaryRepository))
      )
    )
    eventBus.register(
      ClubProjectionSubscriber(clubRepository, playerRepository, globalDictionaryRepository)
    )
    eventBus.register(
      DashboardProjectionSubscriber(
        matchRecordRepository,
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )
    eventBus.register(
      AdvancedStatsProjectionSubscriber(
        advancedStatsPipelineService
      )
    )
    eventBus.register(
      EventCascadeProjectionSubscriber(
        playerRepository,
        clubRepository,
        dashboardRepository,
        advancedStatsBoardRepository,
        eventCascadeRecordRepository,
        advancedStatsPipelineService,
        globalDictionaryRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(
        playerRepository,
        dashboardRepository,
        transactionManager
      ),
      guestSessionService = GuestSessionApplicationService(
        playerRepository,
        guestSessionRepository,
        auditEventRepository,
        transactionManager
      ),
      publicQueryService = PublicQueryService(
        tournamentRepository,
        tableRepository,
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        authorizationService
      ),
      clubService = ClubApplicationService(
        clubRepository,
        playerRepository,
        globalDictionaryRepository,
        dashboardRepository,
        auditEventRepository,
        transactionManager,
        authorizationService
      ),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        tableRepository,
        matchRecordRepository,
        tournamentSettlementRepository,
        auditEventRepository,
        BalancedEloSeatingPolicy(),
        tournamentRuleEngine,
        knockoutStageCoordinator,
        eventBus,
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
        playerRepository,
        knockoutStageCoordinator,
        auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      superAdminService = SuperAdminService(
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        dictionaryNamespaceRepository,
        auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      advancedStatsPipelineService = advancedStatsPipelineService,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      paifuRepository = paifuRepository,
      appealTicketRepository = appealTicketRepository,
      dashboardRepository = dashboardRepository,
      advancedStatsBoardRepository = advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = advancedStatsRecomputeTaskRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      dictionaryNamespaceRepository = dictionaryNamespaceRepository,
      tournamentSettlementRepository = tournamentSettlementRepository,
      eventCascadeRecordRepository = eventCascadeRecordRepository,
      domainEventOutboxRepository = domainEventOutboxRepository,
      auditEventRepository = auditEventRepository,
      eventBus = eventBus,
      authorizationService = authorizationService,
      guestSessionRepository = guestSessionRepository
    )

  def postgres(config: DatabaseConfig): ApplicationContext =
    val connectionFactory = JdbcConnectionFactory(config)
    PostgresSchemaInitializer(connectionFactory).initialize()
    val transactionManager = JdbcTransactionManager(connectionFactory)
    val authorizationService = StrictRbacAuthorizationService()

    val playerRepository = PostgresPlayerRepository(connectionFactory)
    val guestSessionRepository = PostgresGuestSessionRepository(connectionFactory)
    val clubRepository = PostgresClubRepository(connectionFactory)
    val tournamentRepository = PostgresTournamentRepository(connectionFactory)
    val tableRepository = PostgresTableRepository(connectionFactory)
    val matchRecordRepository = PostgresMatchRecordRepository(connectionFactory)
    val paifuRepository = PostgresPaifuRepository(connectionFactory)
    val appealTicketRepository = PostgresAppealTicketRepository(connectionFactory)
    val dashboardRepository = PostgresDashboardRepository(connectionFactory)
    val advancedStatsBoardRepository = PostgresAdvancedStatsBoardRepository(connectionFactory)
    val advancedStatsRecomputeTaskRepository = PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory)
    val globalDictionaryRepository = PostgresGlobalDictionaryRepository(connectionFactory)
    val dictionaryNamespaceRepository = PostgresDictionaryNamespaceRepository(connectionFactory)
    val tournamentSettlementRepository = PostgresTournamentSettlementRepository(connectionFactory)
    val eventCascadeRecordRepository = PostgresEventCascadeRecordRepository(connectionFactory)
    val domainEventOutboxRepository = PostgresDomainEventOutboxRepository(connectionFactory)
    val auditEventRepository = PostgresAuditEventRepository(connectionFactory)

    val eventBus = OutboxBackedDomainEventBus(domainEventOutboxRepository, transactionManager)
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val advancedStatsPipelineService = AdvancedStatsPipelineService(
      paifuRepository,
      matchRecordRepository,
      playerRepository,
      clubRepository,
      advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository,
      transactionManager
    )
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
      RatingProjectionSubscriber(
        playerRepository,
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(globalDictionaryRepository))
      )
    )
    eventBus.register(
      ClubProjectionSubscriber(clubRepository, playerRepository, globalDictionaryRepository)
    )
    eventBus.register(
      DashboardProjectionSubscriber(
        matchRecordRepository,
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )
    eventBus.register(
      AdvancedStatsProjectionSubscriber(
        advancedStatsPipelineService
      )
    )
    eventBus.register(
      EventCascadeProjectionSubscriber(
        playerRepository,
        clubRepository,
        dashboardRepository,
        advancedStatsBoardRepository,
        eventCascadeRecordRepository,
        advancedStatsPipelineService,
        globalDictionaryRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(
        playerRepository,
        dashboardRepository,
        transactionManager
      ),
      guestSessionService = GuestSessionApplicationService(
        playerRepository,
        guestSessionRepository,
        auditEventRepository,
        transactionManager
      ),
      publicQueryService = PublicQueryService(
        tournamentRepository,
        tableRepository,
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        authorizationService
      ),
      clubService = ClubApplicationService(
        clubRepository,
        playerRepository,
        globalDictionaryRepository,
        dashboardRepository,
        auditEventRepository,
        transactionManager,
        authorizationService
      ),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        tableRepository,
        matchRecordRepository,
        tournamentSettlementRepository,
        auditEventRepository,
        BalancedEloSeatingPolicy(),
        tournamentRuleEngine,
        knockoutStageCoordinator,
        eventBus,
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
        playerRepository,
        knockoutStageCoordinator,
        auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      superAdminService = SuperAdminService(
        playerRepository,
        clubRepository,
        globalDictionaryRepository,
        dictionaryNamespaceRepository,
        auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      ),
      advancedStatsPipelineService = advancedStatsPipelineService,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      paifuRepository = paifuRepository,
      appealTicketRepository = appealTicketRepository,
      dashboardRepository = dashboardRepository,
      advancedStatsBoardRepository = advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = advancedStatsRecomputeTaskRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      dictionaryNamespaceRepository = dictionaryNamespaceRepository,
      tournamentSettlementRepository = tournamentSettlementRepository,
      eventCascadeRecordRepository = eventCascadeRecordRepository,
      domainEventOutboxRepository = domainEventOutboxRepository,
      auditEventRepository = auditEventRepository,
      eventBus = eventBus,
      authorizationService = authorizationService,
      guestSessionRepository = guestSessionRepository
    )
