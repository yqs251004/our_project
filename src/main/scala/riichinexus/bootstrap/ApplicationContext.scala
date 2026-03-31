package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.application.service.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.infrastructure.postgres.DatabaseConfig

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
