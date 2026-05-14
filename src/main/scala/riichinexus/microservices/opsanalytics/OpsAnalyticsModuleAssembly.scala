package riichinexus.microservices.opsanalytics

import riichinexus.application.ports.{
  DomainEventBus,
  DomainEventSubscriber,
  TransactionManager
}
import riichinexus.bootstrap.{ApplicationRepositoryContext, OpsAnalyticsModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.opsanalytics.api.{
  AdvancedStatsPipelineService,
  DomainEventOperationsService,
  PerformanceDiagnosticsService
}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

object OpsAnalyticsModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      diagnostics: PerformanceDiagnosticsService,
      advancedStatsService: AdvancedStatsPipelineService,
      domainEventSubscribers: Vector[DomainEventSubscriber],
      eventBus: DomainEventBus,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService,
      playerService: PlayerApplicationService,
      guestSessionService: GuestSessionApplicationService,
      publicQueryService: PublicQueryService,
      clubService: ClubApplicationService,
      tournamentService: TournamentApplicationService,
      tableService: TableLifecycleService,
      appealService: AppealApplicationService
  ): OpsAnalyticsModuleContext =
    val domainEventOperationsService = DomainEventOperationsService(
      repositories.domainEventOutboxRepository,
      repositories.domainEventDeliveryReceiptRepository,
      repositories.domainEventSubscriberCursorRepository,
      domainEventSubscribers,
      repositories.auditEventRepository,
      transactionManager,
      authorizationService
    )
    OpsAnalyticsModuleContext(
      tables = OpsAnalyticsTables(
        advancedStatsRecomputeTaskRepository = repositories.advancedStatsRecomputeTaskRepository,
        eventCascadeRecordRepository = repositories.eventCascadeRecordRepository,
        dashboardRepository = repositories.dashboardRepository,
        advancedStatsBoardRepository = repositories.advancedStatsBoardRepository,
        auditEventRepository = repositories.auditEventRepository
      ),
      advancedStatsService = advancedStatsService,
      demoScenarioService = DemoScenarioAssembly.build(
        repositories = repositories,
        eventBus = eventBus,
        advancedStatsService = advancedStatsService,
        playerService = playerService,
        guestSessionService = guestSessionService,
        publicQueryService = publicQueryService,
        clubService = clubService,
        tournamentService = tournamentService,
        tableService = tableService,
        appealService = appealService
      ),
      domainEventService = domainEventOperationsService,
      performanceDiagnosticsService = diagnostics
    )
