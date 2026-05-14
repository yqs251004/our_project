package riichinexus.microservices.tournament

import riichinexus.application.ports.{DomainEventBus, TransactionManager}
import riichinexus.bootstrap.{ApplicationRepositoryContext, TournamentModuleContext}
import riichinexus.domain.service.*
import riichinexus.microservices.club.api.ClubViewAssembler
import riichinexus.microservices.tournament.api.{
  KnockoutStageCoordinator,
  TableLifecycleService,
  TournamentApplicationService,
  TournamentStageQueryService,
  TournamentViewAssembler
}
import riichinexus.microservices.tournament.tables.TournamentTables

final case class TournamentModuleBuild(
    context: TournamentModuleContext,
    service: TournamentApplicationService,
    tableService: TableLifecycleService,
    views: TournamentViewAssembler,
    knockoutStageCoordinator: KnockoutStageCoordinator
)

object TournamentModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      clubViews: ClubViewAssembler,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService
  ): TournamentModuleBuild =
    val tournamentRuleEngine = DefaultTournamentRuleEngine()
    val knockoutStageCoordinator = KnockoutStageCoordinator(
      repositories.tournamentRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.tableRepository,
      repositories.matchRecordRepository,
      tournamentRuleEngine,
      transactionManager
    )
    val tables = TournamentTables(
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      matchRecordRepository = repositories.matchRecordRepository,
      paifuRepository = repositories.paifuRepository,
      tournamentSettlementRepository = repositories.tournamentSettlementRepository,
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository
    )
    val stageQueries = TournamentStageQueryService(
      tables,
      tournamentRuleEngine,
      knockoutStageCoordinator
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
      stageQueries,
      eventBus,
      transactionManager,
      authorizationService
    )
    val tableService = TableLifecycleService(
      repositories.tableRepository,
      repositories.paifuRepository,
      repositories.matchRecordRepository,
      knockoutStageCoordinator,
      eventBus,
      transactionManager,
      authorizationService
    )
    val views = TournamentViewAssembler(
      tournamentTables = tables,
      stageQueries = stageQueries,
      clubViews = clubViews
    )
    TournamentModuleBuild(
      context = TournamentModuleContext(
        tables = tables,
        service = tournamentService,
        stageQueries = stageQueries,
        views = views,
        tableService = tableService
      ),
      service = tournamentService,
      tableService = tableService,
      views = views,
      knockoutStageCoordinator = knockoutStageCoordinator
    )
