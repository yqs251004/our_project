package riichinexus.bootstrap

import riichinexus.infrastructure.memory.*
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
  PostgresPlayerRepository,
  PostgresSchemaInitializer,
  PostgresTableRepository,
  PostgresTournamentRepository,
  PostgresTournamentSettlementRepository
}
import riichinexus.application.ports.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.events.OutboxBackedDomainEventBus
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import riichinexus.microservices.auth.tables.player.AuthPlayerTable
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.dictionary.domain.DictionaryBackedRatingConfigProvider
import riichinexus.microservices.dictionary.tables.DictionaryTables
import riichinexus.microservices.opsanalytics.projections.{
  AdvancedStatsProjectionSubscriber,
  ClubProjectionSubscriber,
  DashboardProjectionSubscriber,
  EventCascadeProjectionSubscriber,
  RatingProjectionSubscriber
}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables
import riichinexus.microservices.player.domain.PlayerRegistrationOperations
import riichinexus.microservices.player.tables.PlayerTables
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.TournamentStageQueryService
import riichinexus.microservices.tournament.tables.TournamentTables

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
    val diagnostics = PerformanceDiagnosticsService()
    val repositories = PerformanceRepositoryInstrumentation.instrument(wiring.repositories, diagnostics)
    val eventBus = OutboxBackedDomainEventBus(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      wiring.transactionManager,
      eagerDrainOnPublish = wiring.transactionManager == NoOpTransactionManager
    )

    val playerRegistration = PlayerRegistrationOperations(
      repositories.playerRepository,
      repositories.dashboardRepository,
      wiring.transactionManager
    )
    val playerModule = PlayerModuleContext(
      tables = PlayerTables(
        playerRepository = repositories.playerRepository
      ),
      registration = playerRegistration
    )
    val authModule = AuthModuleContext(
      playerTable = AuthPlayerTable(repositories.playerRepository),
      guestSessionTable = GuestSessionTable(repositories.guestSessionRepository),
      playerRegistration = playerRegistration,
      playerRepository = repositories.playerRepository,
      accountCredentialRepository = repositories.accountCredentialRepository,
      authenticatedSessionRepository = repositories.authenticatedSessionRepository,
      guestSessionRepository = repositories.guestSessionRepository,
      clubRepository = repositories.clubRepository,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager
    )
    val clubTables = ClubTables(
      clubRepository = repositories.clubRepository,
      playerRepository = repositories.playerRepository,
      tournamentRepository = repositories.tournamentRepository,
      matchRecordRepository = repositories.matchRecordRepository
    )
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      repositories.tournamentRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.tableRepository,
      repositories.matchRecordRepository,
      tournamentRuleEngine,
      wiring.transactionManager
    )
    val tournamentTables = TournamentTables(
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      paifuRepository = repositories.paifuRepository,
      tournamentSettlementRepository = repositories.tournamentSettlementRepository,
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository
    )
    val tournamentStageQueries = TournamentStageQueryService(
      tournamentTables,
      tournamentRuleEngine,
      knockoutStageCoordinator
    )
    val tournamentModule = TournamentModuleContext(
      tables = tournamentTables,
      tournamentRepository = repositories.tournamentRepository,
      playerRepository = repositories.playerRepository,
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
      tables = clubTables,
      clubRepository = repositories.clubRepository,
      playerRepository = repositories.playerRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      dashboardRepository = repositories.dashboardRepository,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      tournamentModule = tournamentModule
    )
    val tournamentAppealModule = TournamentAppealModuleContext(
      tables = TournamentAppealTables(
        appealTicketRepository = repositories.appealTicketRepository
      ),
      service = AppealApplicationService(
        repositories.appealTicketRepository,
        repositories.tableRepository,
        repositories.playerRepository,
        knockoutStageCoordinator,
        repositories.auditEventRepository,
        eventBus,
        wiring.transactionManager,
        wiring.authorizationService
      )
    )
    val publicQueryTables = PublicQueryTables(
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository
    )
    val publicQueryModule = PublicQueryModuleContext(
      tables = publicQueryTables
    )
    val platformAdminModule = PlatformAdminModuleContext(
      tables = PlatformAdminTables(
        playerRepository = repositories.playerRepository,
        clubRepository = repositories.clubRepository
      ),
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository,
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val domainEventSubscribers = Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(
        repositories.playerRepository,
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(repositories.globalDictionaryRepository))
      ),
      ClubProjectionSubscriber(
        repositories.clubRepository,
        repositories.playerRepository,
        repositories.globalDictionaryRepository
      ),
      DashboardProjectionSubscriber(
        repositories.matchRecordRepository,
        repositories.paifuRepository,
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.dashboardRepository
      ),
      AdvancedStatsProjectionSubscriber(
        repositories.paifuRepository,
        repositories.matchRecordRepository,
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.advancedStatsBoardRepository,
        repositories.advancedStatsRecomputeTaskRepository,
        wiring.transactionManager
      ),
      EventCascadeProjectionSubscriber(
        repositories.paifuRepository,
        repositories.matchRecordRepository,
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.dashboardRepository,
        repositories.advancedStatsBoardRepository,
        repositories.eventCascadeRecordRepository,
        repositories.globalDictionaryRepository
      )
    )
    domainEventSubscribers.foreach(eventBus.register)
    val dictionaryModule = DictionaryModuleContext(
      tables = DictionaryTables(
        globalDictionaryRepository = repositories.globalDictionaryRepository,
        dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository
      ),
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository,
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val opsAnalyticsModule = OpsAnalyticsModuleContext(
      tables = OpsAnalyticsTables(
        advancedStatsRecomputeTaskRepository = repositories.advancedStatsRecomputeTaskRepository,
        eventCascadeRecordRepository = repositories.eventCascadeRecordRepository,
        dashboardRepository = repositories.dashboardRepository,
        advancedStatsBoardRepository = repositories.advancedStatsBoardRepository,
        auditEventRepository = repositories.auditEventRepository
      ),
      paifuRepository = repositories.paifuRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      playerRepository = repositories.playerRepository,
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
