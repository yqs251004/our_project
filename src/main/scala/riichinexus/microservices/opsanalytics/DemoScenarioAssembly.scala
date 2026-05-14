package riichinexus.microservices.opsanalytics

import riichinexus.application.ports.DomainEventBus
import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.opsanalytics.api.{AdvancedStatsPipelineService, DemoScenarioService}
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

object DemoScenarioAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      eventBus: DomainEventBus,
      advancedStatsService: AdvancedStatsPipelineService,
      playerService: PlayerApplicationService,
      guestSessionService: GuestSessionApplicationService,
      publicQueryService: PublicQueryService,
      clubService: ClubApplicationService,
      tournamentService: TournamentApplicationService,
      tableService: TableLifecycleService,
      appealService: AppealApplicationService
  ): DemoScenarioService =
    DemoScenarioService(
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
      advancedStatsPipelineService = advancedStatsService,
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
