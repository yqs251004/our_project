package riichinexus.bootstrap

import _root_.database.postgres.DatabaseConfig
import _root_.domain.*
import _root_.ports.*
import _root_.services.*

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
    _root_.database.ApplicationContext.fromEnvironment(env)

  def inMemory(): ApplicationContext =
    _root_.database.ApplicationContext.inMemory()

  def postgres(config: DatabaseConfig): ApplicationContext =
    _root_.database.ApplicationContext.postgres(config)
