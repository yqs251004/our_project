package riichinexus.bootstrap

import riichinexus.bootstrap.instrumentation.PerformanceDiagnosticsService
import riichinexus.infrastructure.postgres.{
  DatabaseConfig as PostgresRuntimeConfig,
  JdbcConnectionFactory,
  JdbcTransactionManager,
  PostgresAuditEventRepository,
  PostgresDomainEventDeliveryReceiptRepository,
  PostgresDomainEventOutboxRepository,
  PostgresDomainEventSubscriberCursorRepository,
  PostgresEventCascadeRecordRepository,
  PostgresSchemaInitializer
}
import riichinexus.application.ports.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.events.OutboxBackedDomainEventBus
import riichinexus.infrastructure.events.projections.SystemEventCascadeSubscriber
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
    val eventBus = OutboxBackedDomainEventBus(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      wiring.connectionFactory,
      wiring.transactionManager,
      eagerDrainOnPublish = wiring.transactionManager == NoOpTransactionManager
    )

    val playerRegistration = PlayerRegistrationOperations()
    val playerModule = PlayerModuleContext(
      registration = playerRegistration
    )
    val authModule = AuthModuleContext(
      playerRegistration = playerRegistration,
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager
    )
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      tournamentRuleEngine,
      wiring.transactionManager
    )
    val tournamentStageQueries = TournamentStageQueryService(
      tournamentRuleEngine,
      knockoutStageCoordinator
    )
    val tournamentModule = TournamentModuleContext(
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
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      tournamentModule = tournamentModule
    )
    val tournamentAppealModule = TournamentAppealModuleContext(
      service = AppealApplicationService(
        knockoutStageCoordinator,
        repositories.auditEventRepository,
        eventBus,
        wiring.transactionManager,
        wiring.authorizationService
      )
    )
    val publicQueryModule = PublicQueryModuleContext()
    val platformAdminModule = PlatformAdminModuleContext(
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val domainEventSubscribers = Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(
        PairwiseEloRatingService()
      ),
      ClubProjectionSubscriber(),
      DashboardProjectionSubscriber(),
      AdvancedStatsProjectionSubscriber(
        wiring.transactionManager
      ),
      SystemEventCascadeSubscriber(
        repositories.eventCascadeRecordRepository
      )
    )
    domainEventSubscribers.foreach(eventBus.register)
    val opsAnalyticsModule = OpsAnalyticsModuleContext(
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
      publicQueryModule = publicQueryModule,
      opsAnalyticsModule = opsAnalyticsModule,
      tournamentModule = tournamentModule,
      platformAdminModule = platformAdminModule,
      tournamentAppealModule = tournamentAppealModule,
      repositories = repositories,
      authorizationService = wiring.authorizationService
    )
