package database

import _root_.riichinexus.bootstrap.{ApplicationContext => BootstrapApplicationContext}
import database.memory.*
import database.postgres.{
  DatabaseConfig as PostgresRuntimeConfig,
  JdbcConnectionFactory,
  JdbcTransactionManager,
  PostgresAdvancedStatsBoardRepository,
  PostgresAdvancedStatsRecomputeTaskRepository,
  PostgresAccountCredentialRepository,
  PostgresAppealTicketRepository,
  PostgresAuditEventRepository,
  PostgresClubRepository,
  PostgresDashboardRepository,
  PostgresDictionaryNamespaceRepository,
  PostgresDomainEventDeliveryReceiptRepository,
  PostgresDomainEventOutboxRepository,
  PostgresDomainEventSubscriberCursorRepository,
  PostgresEventCascadeRecordRepository,
  PostgresGlobalDictionaryRepository,
  PostgresGuestSessionRepository,
  PostgresAuthenticatedSessionRepository,
  PostgresMatchRecordRepository,
  PostgresPaifuRepository,
  PostgresPlayerRepository,
  PostgresSchemaInitializer,
  PostgresTableRepository,
  PostgresTournamentRepository,
  PostgresTournamentSettlementRepository
}
import domain.*
import events.*
import ports.*
import services.*

private[database] object ApplicationAssembly:

  private final case class RepositoryBundle(
      playerRepository: PlayerRepository,
      accountCredentialRepository: AccountCredentialRepository,
      authenticatedSessionRepository: AuthenticatedSessionRepository,
      guestSessionRepository: GuestSessionRepository,
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
      domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
      domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
      auditEventRepository: AuditEventRepository
  )

  private final case class WiringBundle(
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService,
      repositories: RepositoryBundle
  )

  def fromEnvironment(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    val storageMode = env.get("RIICHI_STORAGE").map(_.trim.toLowerCase)
    if storageMode.contains("postgres") || env.contains("RIICHI_DB_URL") then
      postgres(PostgresRuntimeConfig.fromEnv(env))
    else inMemory()

  def inMemory(): ApplicationContext =
    buildContext(
      WiringBundle(
        transactionManager = NoOpTransactionManager,
        authorizationService = StrictRbacAuthorizationService(),
        repositories = RepositoryBundle(
          playerRepository = InMemoryPlayerRepository(),
          accountCredentialRepository = InMemoryAccountCredentialRepository(),
          authenticatedSessionRepository = InMemoryAuthenticatedSessionRepository(),
          guestSessionRepository = InMemoryGuestSessionRepository(),
          clubRepository = InMemoryClubRepository(),
          tournamentRepository = InMemoryTournamentRepository(),
          tableRepository = InMemoryTableRepository(),
          matchRecordRepository = InMemoryMatchRecordRepository(),
          paifuRepository = InMemoryPaifuRepository(),
          appealTicketRepository = InMemoryAppealTicketRepository(),
          dashboardRepository = InMemoryDashboardRepository(),
          advancedStatsBoardRepository = InMemoryAdvancedStatsBoardRepository(),
          advancedStatsRecomputeTaskRepository = InMemoryAdvancedStatsRecomputeTaskRepository(),
          globalDictionaryRepository = InMemoryGlobalDictionaryRepository(),
          dictionaryNamespaceRepository = InMemoryDictionaryNamespaceRepository(),
          tournamentSettlementRepository = InMemoryTournamentSettlementRepository(),
          eventCascadeRecordRepository = InMemoryEventCascadeRecordRepository(),
          domainEventOutboxRepository = InMemoryDomainEventOutboxRepository(),
          domainEventDeliveryReceiptRepository = InMemoryDomainEventDeliveryReceiptRepository(),
          domainEventSubscriberCursorRepository = InMemoryDomainEventSubscriberCursorRepository(),
          auditEventRepository = InMemoryAuditEventRepository()
        )
      )
    )

  def postgres(config: DatabaseConfig): ApplicationContext =
    postgres(
      PostgresRuntimeConfig(
        url = config.url,
        user = config.user,
        password = config.password,
        schema = config.schema
      )
    )

  def postgres(config: PostgresRuntimeConfig): ApplicationContext =
    val connectionFactory = JdbcConnectionFactory(config)
    PostgresSchemaInitializer(connectionFactory).initialize()
    buildContext(
      WiringBundle(
        transactionManager = JdbcTransactionManager(connectionFactory),
        authorizationService = StrictRbacAuthorizationService(),
        repositories = RepositoryBundle(
          playerRepository = PostgresPlayerRepository(connectionFactory),
          accountCredentialRepository = PostgresAccountCredentialRepository(connectionFactory),
          authenticatedSessionRepository = PostgresAuthenticatedSessionRepository(connectionFactory),
          guestSessionRepository = PostgresGuestSessionRepository(connectionFactory),
          clubRepository = PostgresClubRepository(connectionFactory),
          tournamentRepository = PostgresTournamentRepository(connectionFactory),
          tableRepository = PostgresTableRepository(connectionFactory),
          matchRecordRepository = PostgresMatchRecordRepository(connectionFactory),
          paifuRepository = PostgresPaifuRepository(connectionFactory),
          appealTicketRepository = PostgresAppealTicketRepository(connectionFactory),
          dashboardRepository = PostgresDashboardRepository(connectionFactory),
          advancedStatsBoardRepository = PostgresAdvancedStatsBoardRepository(connectionFactory),
          advancedStatsRecomputeTaskRepository = PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory),
          globalDictionaryRepository = PostgresGlobalDictionaryRepository(connectionFactory),
          dictionaryNamespaceRepository = PostgresDictionaryNamespaceRepository(connectionFactory),
          tournamentSettlementRepository = PostgresTournamentSettlementRepository(connectionFactory),
          eventCascadeRecordRepository = PostgresEventCascadeRecordRepository(connectionFactory),
          domainEventOutboxRepository = PostgresDomainEventOutboxRepository(connectionFactory),
          domainEventDeliveryReceiptRepository = PostgresDomainEventDeliveryReceiptRepository(connectionFactory),
          domainEventSubscriberCursorRepository = PostgresDomainEventSubscriberCursorRepository(connectionFactory),
          auditEventRepository = PostgresAuditEventRepository(connectionFactory)
        )
      )
    )

  private def buildContext(
      wiring: WiringBundle
  ): ApplicationContext =
    val diagnostics = PerformanceDiagnosticsService()
    val repositories = wiring.repositories
    val instrumentedRepositories = repositories.copy(
      playerRepository =
        new riichinexus.application.service.InstrumentedPlayerRepository(repositories.playerRepository, diagnostics),
      clubRepository =
        new riichinexus.application.service.InstrumentedClubRepository(repositories.clubRepository, diagnostics),
      tournamentRepository =
        new riichinexus.application.service.InstrumentedTournamentRepository(repositories.tournamentRepository, diagnostics),
      tableRepository =
        new riichinexus.application.service.InstrumentedTableRepository(repositories.tableRepository, diagnostics),
      matchRecordRepository =
        new riichinexus.application.service.InstrumentedMatchRecordRepository(repositories.matchRecordRepository, diagnostics),
      globalDictionaryRepository =
        new riichinexus.application.service.InstrumentedGlobalDictionaryRepository(repositories.globalDictionaryRepository, diagnostics)
    )
    val eventBus = OutboxBackedDomainEventBus(
      instrumentedRepositories.domainEventOutboxRepository,
      instrumentedRepositories.domainEventDeliveryReceiptRepository,
      instrumentedRepositories.domainEventSubscriberCursorRepository,
      wiring.transactionManager,
      eagerDrainOnPublish = wiring.transactionManager == NoOpTransactionManager
    )
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val advancedStatsPipelineService = AdvancedStatsPipelineService(
      instrumentedRepositories.paifuRepository,
      instrumentedRepositories.matchRecordRepository,
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.advancedStatsBoardRepository,
      instrumentedRepositories.advancedStatsRecomputeTaskRepository,
      wiring.transactionManager
    )
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      instrumentedRepositories.tournamentRepository,
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.tableRepository,
      instrumentedRepositories.matchRecordRepository,
      tournamentRuleEngine,
      wiring.transactionManager
    )
    val domainEventSubscribers = Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(
        instrumentedRepositories.playerRepository,
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(instrumentedRepositories.globalDictionaryRepository))
      ),
      ClubProjectionSubscriber(
        instrumentedRepositories.clubRepository,
        instrumentedRepositories.playerRepository,
        instrumentedRepositories.globalDictionaryRepository
      ),
      DashboardProjectionSubscriber(
        instrumentedRepositories.matchRecordRepository,
        instrumentedRepositories.paifuRepository,
        instrumentedRepositories.playerRepository,
        instrumentedRepositories.clubRepository,
        instrumentedRepositories.dashboardRepository
      ),
      AdvancedStatsProjectionSubscriber(advancedStatsPipelineService),
      EventCascadeProjectionSubscriber(
        instrumentedRepositories.playerRepository,
        instrumentedRepositories.clubRepository,
        instrumentedRepositories.dashboardRepository,
        instrumentedRepositories.advancedStatsBoardRepository,
        instrumentedRepositories.eventCascadeRecordRepository,
        advancedStatsPipelineService,
        instrumentedRepositories.globalDictionaryRepository
      )
    )
    domainEventSubscribers.foreach(eventBus.register)

    val domainEventOperationsService = DomainEventOperationsService(
      instrumentedRepositories.domainEventOutboxRepository,
      instrumentedRepositories.domainEventDeliveryReceiptRepository,
      instrumentedRepositories.domainEventSubscriberCursorRepository,
      domainEventSubscribers,
      instrumentedRepositories.auditEventRepository,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val playerService = PlayerApplicationService(
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.dashboardRepository,
      wiring.transactionManager
    )
    val authService = AuthApplicationService(
      playerService = playerService,
      playerRepository = instrumentedRepositories.playerRepository,
      accountCredentialRepository = instrumentedRepositories.accountCredentialRepository,
      authenticatedSessionRepository = instrumentedRepositories.authenticatedSessionRepository,
      transactionManager = wiring.transactionManager
    )
    val guestSessionService = GuestSessionApplicationService(
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.guestSessionRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.auditEventRepository,
      wiring.transactionManager
    )
    val publicQueryService = PublicQueryService(
      instrumentedRepositories.tournamentRepository,
      instrumentedRepositories.tableRepository,
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.globalDictionaryRepository,
      wiring.authorizationService
    )
    val clubService = ClubApplicationService(
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.globalDictionaryRepository,
      instrumentedRepositories.dashboardRepository,
      instrumentedRepositories.auditEventRepository,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tournamentService = TournamentApplicationService(
      instrumentedRepositories.tournamentRepository,
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.globalDictionaryRepository,
      instrumentedRepositories.tableRepository,
      instrumentedRepositories.matchRecordRepository,
      instrumentedRepositories.tournamentSettlementRepository,
      instrumentedRepositories.auditEventRepository,
      BalancedEloSeatingPolicy(),
      tournamentRuleEngine,
      knockoutStageCoordinator,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tableService = TableLifecycleService(
      instrumentedRepositories.tableRepository,
      instrumentedRepositories.paifuRepository,
      instrumentedRepositories.matchRecordRepository,
      knockoutStageCoordinator,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val appealService = AppealApplicationService(
      instrumentedRepositories.appealTicketRepository,
      instrumentedRepositories.tableRepository,
      instrumentedRepositories.playerRepository,
      knockoutStageCoordinator,
      instrumentedRepositories.auditEventRepository,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val superAdminService = SuperAdminService(
      instrumentedRepositories.playerRepository,
      instrumentedRepositories.clubRepository,
      instrumentedRepositories.globalDictionaryRepository,
      instrumentedRepositories.dictionaryNamespaceRepository,
      instrumentedRepositories.auditEventRepository,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val demoScenarioService = DemoScenarioService(
      playerService = playerService,
      guestSessionService = guestSessionService,
      publicQueryService = publicQueryService,
      clubService = clubService,
      tournamentService = tournamentService,
      tableService = tableService,
      appealService = appealService,
      dashboardRepository = instrumentedRepositories.dashboardRepository,
      advancedStatsBoardRepository = instrumentedRepositories.advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = instrumentedRepositories.advancedStatsRecomputeTaskRepository,
      advancedStatsPipelineService = advancedStatsPipelineService,
      domainEventOutboxRepository = instrumentedRepositories.domainEventOutboxRepository,
      appealTicketRepository = instrumentedRepositories.appealTicketRepository,
      eventBus = eventBus,
      playerRepository = instrumentedRepositories.playerRepository,
      guestSessionRepository = instrumentedRepositories.guestSessionRepository,
      clubRepository = instrumentedRepositories.clubRepository,
      tournamentRepository = instrumentedRepositories.tournamentRepository,
      tableRepository = instrumentedRepositories.tableRepository,
      matchRecordRepository = instrumentedRepositories.matchRecordRepository
    )

    BootstrapApplicationContext(
      playerService = playerService,
      authService = authService,
      guestSessionService = guestSessionService,
      publicQueryService = publicQueryService,
      clubService = clubService,
      tournamentService = tournamentService,
      tableService = tableService,
      appealService = appealService,
      superAdminService = superAdminService,
      advancedStatsPipelineService = advancedStatsPipelineService,
      demoScenarioService = demoScenarioService,
      domainEventOperationsService = domainEventOperationsService,
      performanceDiagnosticsService = diagnostics,
      playerRepository = instrumentedRepositories.playerRepository,
      clubRepository = instrumentedRepositories.clubRepository,
      tournamentRepository = instrumentedRepositories.tournamentRepository,
      tableRepository = instrumentedRepositories.tableRepository,
      matchRecordRepository = instrumentedRepositories.matchRecordRepository,
      paifuRepository = instrumentedRepositories.paifuRepository,
      appealTicketRepository = instrumentedRepositories.appealTicketRepository,
      dashboardRepository = instrumentedRepositories.dashboardRepository,
      advancedStatsBoardRepository = instrumentedRepositories.advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = instrumentedRepositories.advancedStatsRecomputeTaskRepository,
      globalDictionaryRepository = instrumentedRepositories.globalDictionaryRepository,
      dictionaryNamespaceRepository = instrumentedRepositories.dictionaryNamespaceRepository,
      tournamentSettlementRepository = instrumentedRepositories.tournamentSettlementRepository,
      eventCascadeRecordRepository = instrumentedRepositories.eventCascadeRecordRepository,
      domainEventOutboxRepository = instrumentedRepositories.domainEventOutboxRepository,
      domainEventDeliveryReceiptRepository = instrumentedRepositories.domainEventDeliveryReceiptRepository,
      domainEventSubscriberCursorRepository = instrumentedRepositories.domainEventSubscriberCursorRepository,
      auditEventRepository = instrumentedRepositories.auditEventRepository,
      eventBus = eventBus,
      authorizationService = wiring.authorizationService,
      accountCredentialRepository = instrumentedRepositories.accountCredentialRepository,
      authenticatedSessionRepository = instrumentedRepositories.authenticatedSessionRepository,
      guestSessionRepository = instrumentedRepositories.guestSessionRepository
    )
