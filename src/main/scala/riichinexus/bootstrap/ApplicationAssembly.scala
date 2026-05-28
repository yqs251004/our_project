package riichinexus.bootstrap

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
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.events.OutboxBackedDomainEventBus
import riichinexus.infrastructure.events.projections.SystemEventCascadeSubscriber
import riichinexus.microservices.opsanalytics.projections.{
  AdvancedStatsProjectionSubscriber,
  ClubProjectionSubscriber,
  DashboardProjectionSubscriber,
  RatingProjectionSubscriber
}
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.{
  TournamentPaifuArchiveService,
  TournamentSettlementCoordinator,
  TournamentStageCompletionCoordinator
}
import riichinexus.system.instrumentation.PerformanceDiagnosticsService

object ApplicationAssembly:

  private final case class WiringBundle(
      transactionManager: TransactionManager,
      authorizationService: AuthorizationPolicy,
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
        authorizationService = AuthorizationPolicy.strict,
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

    val playerModule = PlayerModuleContext()
    val authModule = AuthModuleContext(
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager
    )
    val tournamentPaifuArchiveService = new TournamentPaifuArchiveService(
      repositories.auditEventRepository,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tournamentSettlementCoordinator = new TournamentSettlementCoordinator(
      repositories.auditEventRepository,
      eventBus,
      wiring.transactionManager,
      wiring.authorizationService
    )
    val tournamentStageCompletionCoordinator = new TournamentStageCompletionCoordinator(
      wiring.authorizationService
    )
    val tournamentModule = TournamentModuleContext(
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      paifuArchiveService = tournamentPaifuArchiveService,
      settlementCoordinator = tournamentSettlementCoordinator,
      stageCompletionCoordinator = tournamentStageCompletionCoordinator
    )
    val clubModule = ClubModuleContext(
      auditEventRepository = repositories.auditEventRepository,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService,
      tournamentModule = tournamentModule
    )
    val tournamentAppealModule = TournamentAppealModuleContext(
      service = AppealApplicationService(
        repositories.auditEventRepository,
        eventBus,
        wiring.transactionManager,
        wiring.authorizationService
      )
    )
    val platformAdminModule = PlatformAdminModuleContext(
      auditEventRepository = repositories.auditEventRepository,
      eventBus = eventBus,
      transactionManager = wiring.transactionManager,
      authorizationService = wiring.authorizationService
    )
    val domainEventSubscribers = Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(),
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
      authorizationService = wiring.authorizationService
    )

    new ApplicationContext(
      connectionFactory = wiring.connectionFactory,
      authModule = authModule,
      playerModule = playerModule,
      clubModule = clubModule,
      opsAnalyticsModule = opsAnalyticsModule,
      tournamentModule = tournamentModule,
      platformAdminModule = platformAdminModule,
      tournamentAppealModule = tournamentAppealModule,
      repositories = repositories,
      authorizationService = wiring.authorizationService,
      performanceDiagnosticsService = diagnostics
    )
