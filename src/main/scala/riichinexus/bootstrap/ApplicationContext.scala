package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.application.service.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.infrastructure.postgres.DatabaseConfig

final case class ApplicationContext(
    playerService: PlayerApplicationService,
    authService: AuthApplicationService,
    guestSessionService: GuestSessionApplicationService,
    publicQueryService: PublicQueryService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    appealService: AppealApplicationService,
    superAdminService: SuperAdminService,
    advancedStatsPipelineService: AdvancedStatsPipelineService,
    demoScenarioService: DemoScenarioService,
    domainEventOperationsService: DomainEventOperationsService,
    performanceDiagnosticsService: PerformanceDiagnosticsService,
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
    domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    authorizationService: AuthorizationService,
    accountCredentialRepository: AccountCredentialRepository,
    authenticatedSessionRepository: AuthenticatedSessionRepository,
    guestSessionRepository: GuestSessionRepository
)

object ApplicationContext:

  def fromEnvironment(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    ApplicationAssembly.fromEnvironment(env)

  def inMemory(): ApplicationContext =
    ApplicationAssembly.inMemory()

  def postgres(config: DatabaseConfig): ApplicationContext =
    ApplicationAssembly.postgres(config)

  def postgres(config: TemplateDatabaseConfig): ApplicationContext =
    ApplicationAssembly.postgres(config)
