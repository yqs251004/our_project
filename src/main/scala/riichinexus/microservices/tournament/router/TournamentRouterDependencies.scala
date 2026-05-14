package riichinexus.microservices.tournament.router

import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.tables.TournamentTables
import riichinexus.api.http.RouteSupport

private[router] final case class TournamentRouterDependencies(
    tables: TournamentTables,
    service: TournamentApplicationService,
    stageQueries: TournamentStageQueryService,
    views: TournamentViewAssembler,
    tableService: TableLifecycleService
)

private[router] object TournamentRouterDependencies:
  def from(support: RouteSupport): TournamentRouterDependencies =
    val module = support.tournamentModule
    TournamentRouterDependencies(
      tables = module.tables,
      service = module.service,
      stageQueries = module.stageQueries,
      views = module.views,
      tableService = module.tableService
    )
