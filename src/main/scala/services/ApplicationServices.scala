package services

import domain.*
import ports.*

type KnockoutStageCoordinator = riichinexus.application.service.KnockoutStageCoordinator
type PlayerApplicationService = riichinexus.application.service.PlayerApplicationService
type GuestSessionApplicationService = riichinexus.application.service.GuestSessionApplicationService
type PublicQueryService = riichinexus.application.service.PublicQueryService
type DemoScenarioService = riichinexus.application.service.DemoScenarioService
type DomainEventOperationsService = riichinexus.application.service.DomainEventOperationsService
type ClubApplicationService = riichinexus.application.service.ClubApplicationService
type TournamentApplicationService = riichinexus.application.service.TournamentApplicationService
type TableLifecycleService = riichinexus.application.service.TableLifecycleService
type AppealApplicationService = riichinexus.application.service.AppealApplicationService
type SuperAdminService = riichinexus.application.service.SuperAdminService
type AdvancedStatsPipelineService = riichinexus.application.service.AdvancedStatsPipelineService
type DictionaryBackedRatingConfigProvider = riichinexus.application.service.DictionaryBackedRatingConfigProvider
type RatingProjectionSubscriber = riichinexus.application.service.RatingProjectionSubscriber
type ClubProjectionSubscriber = riichinexus.application.service.ClubProjectionSubscriber
type DashboardProjectionSubscriber = riichinexus.application.service.DashboardProjectionSubscriber
type AdvancedStatsProjectionSubscriber = riichinexus.application.service.AdvancedStatsProjectionSubscriber
type EventCascadeProjectionSubscriber = riichinexus.application.service.EventCascadeProjectionSubscriber

object KnockoutStageCoordinator:
  def apply(
      tournamentRepository: TournamentRepository,
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      tableRepository: TableRepository,
      matchRecordRepository: MatchRecordRepository,
      tournamentRuleEngine: TournamentRuleEngine,
      transactionManager: TransactionManager = NoOpTransactionManager
  ): KnockoutStageCoordinator =
    new riichinexus.application.service.KnockoutStageCoordinator(
      tournamentRepository = tournamentRepository,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      tournamentRuleEngine = tournamentRuleEngine,
      transactionManager = transactionManager
    )

object PlayerApplicationService:
  def apply(
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository,
      transactionManager: TransactionManager = NoOpTransactionManager
  ): PlayerApplicationService =
    new riichinexus.application.service.PlayerApplicationService(
      playerRepository = playerRepository,
      dashboardRepository = dashboardRepository,
      transactionManager = transactionManager
    )

object GuestSessionApplicationService:
  def apply(
      playerRepository: PlayerRepository,
      guestSessionRepository: GuestSessionRepository,
      clubRepository: ClubRepository,
      auditEventRepository: AuditEventRepository,
      transactionManager: TransactionManager = NoOpTransactionManager
  ): GuestSessionApplicationService =
    new riichinexus.application.service.GuestSessionApplicationService(
      playerRepository = playerRepository,
      guestSessionRepository = guestSessionRepository,
      clubRepository = clubRepository,
      auditEventRepository = auditEventRepository,
      transactionManager = transactionManager
    )

object PublicQueryService:
  def apply(
      tournamentRepository: TournamentRepository,
      tableRepository: TableRepository,
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): PublicQueryService =
    new riichinexus.application.service.PublicQueryService(
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      authorizationService = authorizationService
    )

object DemoScenarioService:
  def apply(
      playerService: PlayerApplicationService,
      guestSessionService: GuestSessionApplicationService,
      publicQueryService: PublicQueryService,
      clubService: ClubApplicationService,
      tournamentService: TournamentApplicationService,
      tableService: TableLifecycleService,
      appealService: AppealApplicationService,
      dashboardRepository: DashboardRepository,
      advancedStatsBoardRepository: AdvancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
      advancedStatsPipelineService: AdvancedStatsPipelineService,
      domainEventOutboxRepository: DomainEventOutboxRepository,
      appealTicketRepository: AppealTicketRepository,
      eventBus: DomainEventBus,
      playerRepository: PlayerRepository,
      guestSessionRepository: GuestSessionRepository,
      clubRepository: ClubRepository,
      tournamentRepository: TournamentRepository,
      tableRepository: TableRepository,
      matchRecordRepository: MatchRecordRepository
  ): DemoScenarioService =
    new riichinexus.application.service.DemoScenarioService(
      playerService = playerService,
      guestSessionService = guestSessionService,
      publicQueryService = publicQueryService,
      clubService = clubService,
      tournamentService = tournamentService,
      tableService = tableService,
      appealService = appealService,
      dashboardRepository = dashboardRepository,
      advancedStatsBoardRepository = advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = advancedStatsRecomputeTaskRepository,
      advancedStatsPipelineService = advancedStatsPipelineService,
      domainEventOutboxRepository = domainEventOutboxRepository,
      appealTicketRepository = appealTicketRepository,
      eventBus = eventBus,
      playerRepository = playerRepository,
      guestSessionRepository = guestSessionRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository
    )

object DomainEventOperationsService:
  def apply(
      outboxRepository: DomainEventOutboxRepository,
      deliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
      subscriberCursorRepository: DomainEventSubscriberCursorRepository,
      subscribers: Vector[DomainEventSubscriber],
      auditEventRepository: AuditEventRepository,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): DomainEventOperationsService =
    new riichinexus.application.service.DomainEventOperationsService(
      outboxRepository = outboxRepository,
      deliveryReceiptRepository = deliveryReceiptRepository,
      subscriberCursorRepository = subscriberCursorRepository,
      subscribers = subscribers,
      auditEventRepository = auditEventRepository,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object ClubApplicationService:
  def apply(
      clubRepository: ClubRepository,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dashboardRepository: DashboardRepository,
      auditEventRepository: AuditEventRepository,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): ClubApplicationService =
    new riichinexus.application.service.ClubApplicationService(
      clubRepository = clubRepository,
      playerRepository = playerRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      dashboardRepository = dashboardRepository,
      auditEventRepository = auditEventRepository,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object TournamentApplicationService:
  def apply(
      tournamentRepository: TournamentRepository,
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      tableRepository: TableRepository,
      matchRecordRepository: MatchRecordRepository,
      tournamentSettlementRepository: TournamentSettlementRepository,
      auditEventRepository: AuditEventRepository,
      seatingPolicy: SeatingPolicy,
      tournamentRuleEngine: TournamentRuleEngine,
      knockoutStageCoordinator: KnockoutStageCoordinator,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): TournamentApplicationService =
    new riichinexus.application.service.TournamentApplicationService(
      tournamentRepository = tournamentRepository,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      tableRepository = tableRepository,
      matchRecordRepository = matchRecordRepository,
      tournamentSettlementRepository = tournamentSettlementRepository,
      auditEventRepository = auditEventRepository,
      seatingPolicy = seatingPolicy,
      tournamentRuleEngine = tournamentRuleEngine,
      knockoutStageCoordinator = knockoutStageCoordinator,
      eventBus = eventBus,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object TableLifecycleService:
  def apply(
      tableRepository: TableRepository,
      paifuRepository: PaifuRepository,
      matchRecordRepository: MatchRecordRepository,
      knockoutStageCoordinator: KnockoutStageCoordinator,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): TableLifecycleService =
    new riichinexus.application.service.TableLifecycleService(
      tableRepository = tableRepository,
      paifuRepository = paifuRepository,
      matchRecordRepository = matchRecordRepository,
      knockoutStageCoordinator = knockoutStageCoordinator,
      eventBus = eventBus,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object AppealApplicationService:
  def apply(
      appealTicketRepository: AppealTicketRepository,
      tableRepository: TableRepository,
      playerRepository: PlayerRepository,
      knockoutStageCoordinator: KnockoutStageCoordinator,
      auditEventRepository: AuditEventRepository,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): AppealApplicationService =
    new riichinexus.application.service.AppealApplicationService(
      appealTicketRepository = appealTicketRepository,
      tableRepository = tableRepository,
      playerRepository = playerRepository,
      knockoutStageCoordinator = knockoutStageCoordinator,
      auditEventRepository = auditEventRepository,
      eventBus = eventBus,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object SuperAdminService:
  def apply(
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dictionaryNamespaceRepository: DictionaryNamespaceRepository,
      auditEventRepository: AuditEventRepository,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager = NoOpTransactionManager,
      authorizationService: AuthorizationService = NoOpAuthorizationService
  ): SuperAdminService =
    new riichinexus.application.service.SuperAdminService(
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      globalDictionaryRepository = globalDictionaryRepository,
      dictionaryNamespaceRepository = dictionaryNamespaceRepository,
      auditEventRepository = auditEventRepository,
      eventBus = eventBus,
      transactionManager = transactionManager,
      authorizationService = authorizationService
    )

object AdvancedStatsPipelineService:
  def apply(
      paifuRepository: PaifuRepository,
      matchRecordRepository: MatchRecordRepository,
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      advancedStatsBoardRepository: AdvancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
      transactionManager: TransactionManager
  ): AdvancedStatsPipelineService =
    new riichinexus.application.service.AdvancedStatsPipelineService(
      paifuRepository = paifuRepository,
      matchRecordRepository = matchRecordRepository,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      advancedStatsBoardRepository = advancedStatsBoardRepository,
      advancedStatsRecomputeTaskRepository = advancedStatsRecomputeTaskRepository,
      transactionManager = transactionManager
    )

object DictionaryBackedRatingConfigProvider:
  def apply(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): DictionaryBackedRatingConfigProvider =
    new riichinexus.application.service.DictionaryBackedRatingConfigProvider(
      globalDictionaryRepository = globalDictionaryRepository
    )

object RatingProjectionSubscriber:
  def apply(
      playerRepository: PlayerRepository,
      ratingService: RatingService
  ): RatingProjectionSubscriber =
    new riichinexus.application.service.RatingProjectionSubscriber(
      playerRepository = playerRepository,
      ratingService = ratingService
    )

object ClubProjectionSubscriber:
  def apply(
      clubRepository: ClubRepository,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): ClubProjectionSubscriber =
    new riichinexus.application.service.ClubProjectionSubscriber(
      clubRepository = clubRepository,
      playerRepository = playerRepository,
      globalDictionaryRepository = globalDictionaryRepository
    )

object DashboardProjectionSubscriber:
  def apply(
      matchRecordRepository: MatchRecordRepository,
      paifuRepository: PaifuRepository,
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      dashboardRepository: DashboardRepository
  ): DashboardProjectionSubscriber =
    new riichinexus.application.service.DashboardProjectionSubscriber(
      matchRecordRepository = matchRecordRepository,
      paifuRepository = paifuRepository,
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      dashboardRepository = dashboardRepository
    )

object AdvancedStatsProjectionSubscriber:
  def apply(
      advancedStatsPipelineService: AdvancedStatsPipelineService
  ): AdvancedStatsProjectionSubscriber =
    new riichinexus.application.service.AdvancedStatsProjectionSubscriber(
      advancedStatsPipelineService = advancedStatsPipelineService
    )

object EventCascadeProjectionSubscriber:
  def apply(
      playerRepository: PlayerRepository,
      clubRepository: ClubRepository,
      dashboardRepository: DashboardRepository,
      advancedStatsBoardRepository: AdvancedStatsBoardRepository,
      eventCascadeRecordRepository: EventCascadeRecordRepository,
      advancedStatsPipelineService: AdvancedStatsPipelineService,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): EventCascadeProjectionSubscriber =
    new riichinexus.application.service.EventCascadeProjectionSubscriber(
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      dashboardRepository = dashboardRepository,
      advancedStatsBoardRepository = advancedStatsBoardRepository,
      eventCascadeRecordRepository = eventCascadeRecordRepository,
      advancedStatsPipelineService = advancedStatsPipelineService,
      globalDictionaryRepository = globalDictionaryRepository
    )
