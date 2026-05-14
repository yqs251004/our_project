package riichinexus.bootstrap

import riichinexus.infrastructure.memory.*
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
  PostgresPlayerRepository,
  PostgresSchemaInitializer,
  PostgresTableRepository,
  PostgresTournamentRepository,
  PostgresTournamentSettlementRepository
}
import riichinexus.application.ports.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.events.OutboxBackedDomainEventBus
import riichinexus.microservices.auth.AuthModuleAssembly
import riichinexus.microservices.club.ClubModuleAssembly
import riichinexus.microservices.dictionary.DictionaryModuleAssembly
import riichinexus.microservices.opsanalytics.{
  AdvancedStatsPipelineAssembly,
  OpsAnalyticsDiagnosticsAssembly,
  OpsAnalyticsModuleAssembly,
  OpsAnalyticsProjectionAssembly,
  OpsAnalyticsRepositoryInstrumentation
}
import riichinexus.microservices.platformadmin.PlatformAdminModuleAssembly
import riichinexus.microservices.player.PlayerModuleAssembly
import riichinexus.microservices.publicquery.PublicQueryModuleAssembly
import riichinexus.microservices.tournament.TournamentModuleAssembly
import riichinexus.microservices.tournament.appeal.TournamentAppealModuleAssembly

object ApplicationAssembly:

  private final case class WiringBundle(
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService,
      repositories: ApplicationRepositoryContext
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
        repositories = ApplicationRepositoryContext(
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
        repositories = ApplicationRepositoryContext(
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
    val diagnostics = OpsAnalyticsDiagnosticsAssembly.build()
    val repositories = OpsAnalyticsRepositoryInstrumentation.instrument(wiring.repositories, diagnostics)
    val eventBus = OutboxBackedDomainEventBus(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      wiring.transactionManager,
      eagerDrainOnPublish = wiring.transactionManager == NoOpTransactionManager
    )

    val playerModule = PlayerModuleAssembly.build(
      repositories = repositories,
      transactionManager = wiring.transactionManager
    )
    val authModule = AuthModuleAssembly.build(
      repositories = repositories,
      transactionManager = wiring.transactionManager,
      playerService = playerModule.service
    )
    val advancedStatsService = AdvancedStatsPipelineAssembly.build(
      repositories = repositories,
      transactionManager = wiring.transactionManager
    )
    val clubModuleCore = ClubModuleAssembly.buildCore(
      repositories = repositories,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val tournamentModule = TournamentModuleAssembly.build(
      repositories = repositories,
      clubViews = clubModuleCore.views,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val clubModule = ClubModuleAssembly.context(
      core = clubModuleCore,
      tournamentService = tournamentModule.service,
      tournamentViews = tournamentModule.views
    )
    val tournamentAppealModule = TournamentAppealModuleAssembly.build(
      repositories = repositories,
      knockoutStageCoordinator = tournamentModule.knockoutStageCoordinator,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val publicQueryModule = PublicQueryModuleAssembly.build(
      repositories = repositories,
      authorizationService = wiring.authorizationService,
      clubViews = clubModuleCore.views,
      tournamentViews = tournamentModule.views
    )
    val platformAdminModule = PlatformAdminModuleAssembly.build(
      repositories = repositories,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val domainEventSubscribers = OpsAnalyticsProjectionAssembly.subscribers(
      repositories = repositories,
      advancedStatsService = advancedStatsService
    )
    domainEventSubscribers.foreach(eventBus.register)
    val dictionaryModule = DictionaryModuleAssembly.build(
      repositories = repositories,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val opsAnalyticsModule = OpsAnalyticsModuleAssembly.build(
      repositories = repositories,
      diagnostics = diagnostics,
      advancedStatsService = advancedStatsService,
      domainEventSubscribers = domainEventSubscribers,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      playerService = playerModule.service,
      guestSessionService = authModule.guestSessionService,
      publicQueryService = publicQueryModule.service,
      clubService = clubModuleCore.service,
      tournamentService = tournamentModule.service,
      tableService = tournamentModule.tableService,
      appealService = tournamentAppealModule.service
    )

    new ApplicationContext(
      authModule = authModule,
      playerModule = playerModule.context,
      clubModule = clubModule,
      dictionaryModule = dictionaryModule,
      publicQueryModule = publicQueryModule.context,
      opsAnalyticsModule = opsAnalyticsModule,
      tournamentModule = tournamentModule.context,
      platformAdminModule = platformAdminModule,
      tournamentAppealModule = tournamentAppealModule,
      repositories = repositories,
      authorizationService = wiring.authorizationService
    )
