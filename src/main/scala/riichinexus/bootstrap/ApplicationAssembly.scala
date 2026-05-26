package riichinexus.bootstrap

import riichinexus.bootstrap.instrumentation.{
  PerformanceDiagnosticsService,
  PerformanceRepositoryInstrumentation
}
import riichinexus.infrastructure.postgres.{
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
  PostgresSchemaInitializer,
  PostgresTableRepository,
  PostgresTournamentRepository,
  PostgresTournamentSettlementRepository
}
import riichinexus.application.ports.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.events.OutboxBackedDomainEventBus
import riichinexus.infrastructure.events.projections.SystemEventCascadeSubscriber
import riichinexus.microservices.dictionary.domain.DictionaryBackedRatingConfigProvider
import riichinexus.microservices.opsanalytics.projections.{
  AdvancedStatsProjectionSubscriber,
  ClubProjectionSubscriber,
  DashboardProjectionSubscriber,
  RatingProjectionSubscriber
}
import riichinexus.microservices.player.domain.PlayerRegistrationOperations
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.TournamentStageQueryService

object ApplicationAssembly:

  private final case class WiringBundle(
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService,
      repositories: ApplicationRepositoryContext,
      connectionFactory: JdbcConnectionFactory
  )

  def fromEnvironment(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    val storageMode = env.get("RIICHI_STORAGE").map(_.trim.toLowerCase)
    if storageMode.contains("postgres") || env.contains("RIICHI_DB_URL") then
      postgres(PostgresRuntimeConfig.fromEnv(env))
    else inMemory()

  def inMemory(): ApplicationContext =
    throw IllegalStateException("In-memory storage is no longer supported; configure PostgreSQL storage")

  def postgres(config: TemplateDatabaseConfig): ApplicationContext =
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
        connectionFactory = connectionFactory,
        repositories = ApplicationRepositoryContext(
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
    val repositories = PerformanceRepositoryInstrumentation.instrument(wiring.repositories, diagnostics)
    val eventBus = OutboxBackedDomainEventBus(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      wiring.connectionFactory,
      wiring.transactionManager,
      eagerDrainOnPublish = wiring.transactionManager == NoOpTransactionManager
    )

    val playerRegistration = PlayerRegistrationOperations(
      repositories.dashboardRepository
    )
    val playerModule = PlayerModuleContext(
      registration = playerRegistration
    )
    val authModule = AuthModuleContext(
      playerRegistration = playerRegistration,
      clubRepository = repositories.clubRepository,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager
    )
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      repositories.tournamentRepository,
      repositories.clubRepository,
      repositories.tableRepository,
      repositories.matchRecordRepository,
      tournamentRuleEngine,
      wiring.transactionManager
    )
    val tournamentStageQueries = TournamentStageQueryService(
      tournamentRuleEngine,
      knockoutStageCoordinator
    )
    val tournamentModule = TournamentModuleContext(
      tournamentRepository = repositories.tournamentRepository,
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      tableRepository = repositories.tableRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      paifuRepository = repositories.paifuRepository,
      tournamentSettlementRepository = repositories.tournamentSettlementRepository,
      auditEventRepository = repositories.auditEventRepository,
      seatingPolicy = BalancedEloSeatingPolicy(),
      tournamentRuleEngine = tournamentRuleEngine,
      knockoutStageCoordinator = knockoutStageCoordinator,
      stageQueries = tournamentStageQueries,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val clubModule = ClubModuleContext(
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      dashboardRepository = repositories.dashboardRepository,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      tournamentModule = tournamentModule
    )
    val tournamentAppealModule = TournamentAppealModuleContext(
      service = AppealApplicationService(
        repositories.appealTicketRepository,
        repositories.tableRepository,
        knockoutStageCoordinator,
        repositories.auditEventRepository,
        eventBus,
        wiring.transactionManager,
        wiring.authorizationService
      )
    )
    val publicQueryModule = PublicQueryModuleContext()
    val platformAdminModule = PlatformAdminModuleContext(
      clubRepository = repositories.clubRepository,
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val domainEventSubscribers = Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(repositories.globalDictionaryRepository))
      ),
      ClubProjectionSubscriber(
        repositories.clubRepository,
        repositories.globalDictionaryRepository
      ),
      DashboardProjectionSubscriber(
        repositories.matchRecordRepository,
        repositories.paifuRepository,
        repositories.clubRepository,
        repositories.dashboardRepository
      ),
      AdvancedStatsProjectionSubscriber(
        repositories.paifuRepository,
        repositories.matchRecordRepository,
        repositories.clubRepository,
        repositories.advancedStatsBoardRepository,
        repositories.advancedStatsRecomputeTaskRepository,
        wiring.transactionManager
      ),
      SystemEventCascadeSubscriber(
        repositories.paifuRepository,
        repositories.matchRecordRepository,
        repositories.clubRepository,
        repositories.dashboardRepository,
        repositories.advancedStatsBoardRepository,
        repositories.eventCascadeRecordRepository,
        repositories.globalDictionaryRepository
      )
    )
    domainEventSubscribers.foreach(eventBus.register)
    val dictionaryModule = DictionaryModuleContext(
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository,
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val opsAnalyticsModule = OpsAnalyticsModuleContext(
      paifuRepository = repositories.paifuRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      clubRepository = repositories.clubRepository,
      advancedStatsBoardRepository = repositories.advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = repositories.advancedStatsRecomputeTaskRepository,
      domainEventOutboxRepository = repositories.domainEventOutboxRepository,
      domainEventDeliveryReceiptRepository = repositories.domainEventDeliveryReceiptRepository,
      domainEventSubscriberCursorRepository = repositories.domainEventSubscriberCursorRepository,
      domainEventSubscribers = domainEventSubscribers,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      performanceDiagnosticsService = diagnostics
    )

    new ApplicationContext(
      connectionFactory = wiring.connectionFactory,
      authModule = authModule,
      playerModule = playerModule,
      clubModule = clubModule,
      dictionaryModule = dictionaryModule,
      publicQueryModule = publicQueryModule,
      opsAnalyticsModule = opsAnalyticsModule,
      tournamentModule = tournamentModule,
      platformAdminModule = platformAdminModule,
      tournamentAppealModule = tournamentAppealModule,
      repositories = repositories,
      authorizationService = wiring.authorizationService
    )
