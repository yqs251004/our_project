package database

import _root_.riichinexus.bootstrap.{ApplicationContext => BootstrapApplicationContext}
import database.memory.*
import database.postgres.{
  DatabaseConfig as PostgresRuntimeConfig,
  JdbcConnectionFactory,
  JdbcTransactionManager,
  PostgresAdvancedStatsBoardRepository,
  PostgresAdvancedStatsRecomputeTaskRepository,
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
    val repositories = wiring.repositories
    val eventBus = OutboxBackedDomainEventBus(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      wiring.transactionManager
    )
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val advancedStatsPipelineService = AdvancedStatsPipelineService(
      repositories.paifuRepository,
      repositories.matchRecordRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.advancedStatsBoardRepository,
      repositories.advancedStatsRecomputeTaskRepository,
      wiring.transactionManager
    )
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      repositories.tournamentRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.tableRepository,
      repositories.matchRecordRepository,
      tournamentRuleEngine,
      wiring.transactionManager
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
      AdvancedStatsProjectionSubscriber(advancedStatsPipelineService),
      EventCascadeProjectionSubscriber(
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.dashboardRepository,
        repositories.advancedStatsBoardRepository,
        repositories.eventCascadeRecordRepository,
        advancedStatsPipelineService,
        repositories.globalDictionaryRepository
      )
    )
    domainEventSubscribers.foreach(eventBus.register)

    val domainEventOperationsService = DomainEventOperationsService(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      domainEventSubscribers,
      repositories.auditEventRepository,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val playerService = PlayerApplicationService(
      repositories.playerRepository,
      repositories.dashboardRepository,
      wiring.transactionManager
    )
    val guestSessionService = GuestSessionApplicationService(
      repositories.playerRepository,
      repositories.guestSessionRepository,
      repositories.clubRepository,
      repositories.auditEventRepository,
      wiring.transactionManager
    )
    val publicQueryService = PublicQueryService(
      repositories.tournamentRepository,
      repositories.tableRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.globalDictionaryRepository,
      wiring.authorizationService
    )
    val clubService = ClubApplicationService(
      repositories.clubRepository,
      repositories.playerRepository,
      repositories.globalDictionaryRepository,
      repositories.dashboardRepository,
      repositories.auditEventRepository,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tournamentService = TournamentApplicationService(
      repositories.tournamentRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.globalDictionaryRepository,
      repositories.tableRepository,
      repositories.matchRecordRepository,
      repositories.tournamentSettlementRepository,
      repositories.auditEventRepository,
      BalancedEloSeatingPolicy(),
      tournamentRuleEngine,
      knockoutStageCoordinator,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tableService = TableLifecycleService(
      repositories.tableRepository,
      repositories.paifuRepository,
      repositories.matchRecordRepository,
      knockoutStageCoordinator,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val appealService = AppealApplicationService(
      repositories.appealTicketRepository,
      repositories.tableRepository,
      repositories.playerRepository,
      knockoutStageCoordinator,
      repositories.auditEventRepository,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val superAdminService = SuperAdminService(
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.globalDictionaryRepository,
      repositories.dictionaryNamespaceRepository,
      repositories.auditEventRepository,
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
      dashboardRepository = repositories.dashboardRepository,
      advancedStatsBoardRepository = repositories.advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = repositories.advancedStatsRecomputeTaskRepository,
      advancedStatsPipelineService = advancedStatsPipelineService,
      domainEventOutboxRepository = repositories.domainEventOutboxRepository,
      appealTicketRepository = repositories.appealTicketRepository,
      eventBus = eventBus,
      playerRepository = repositories.playerRepository,
      guestSessionRepository = repositories.guestSessionRepository,
      clubRepository = repositories.clubRepository,
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      matchRecordRepository = repositories.matchRecordRepository
    )

    BootstrapApplicationContext(
      playerService = playerService,
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
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository,
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      paifuRepository = repositories.paifuRepository,
      appealTicketRepository = repositories.appealTicketRepository,
      dashboardRepository = repositories.dashboardRepository,
      advancedStatsBoardRepository = repositories.advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = repositories.advancedStatsRecomputeTaskRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository,
      dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository,
      tournamentSettlementRepository = repositories.tournamentSettlementRepository,
      eventCascadeRecordRepository = repositories.eventCascadeRecordRepository,
      domainEventOutboxRepository = repositories.domainEventOutboxRepository,
      domainEventDeliveryReceiptRepository = repositories.domainEventDeliveryReceiptRepository,
      domainEventSubscriberCursorRepository = repositories.domainEventSubscriberCursorRepository,
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      authorizationService = wiring.authorizationService,
      guestSessionRepository = repositories.guestSessionRepository
    )
