package riichinexus.microservices.club

import riichinexus.application.ports.TransactionManager
import riichinexus.bootstrap.{ApplicationRepositoryContext, ClubModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.club.api.{ClubApplicationService, ClubViewAssembler}
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.tournament.api.{TournamentApplicationService, TournamentViewAssembler}

final case class ClubModuleCore(
    tables: ClubTables,
    service: ClubApplicationService,
    views: ClubViewAssembler
)

object ClubModuleAssembly:
  def buildCore(
      repositories: ApplicationRepositoryContext,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService
  ): ClubModuleCore =
    val tables = ClubTables(
      clubRepository = repositories.clubRepository,
      playerRepository = repositories.playerRepository,
      tournamentRepository = repositories.tournamentRepository,
      matchRecordRepository = repositories.matchRecordRepository
    )
    ClubModuleCore(
      tables = tables,
      service = ClubApplicationService(
        repositories.clubRepository,
        repositories.playerRepository,
        repositories.globalDictionaryRepository,
        repositories.dashboardRepository,
        repositories.auditEventRepository,
        transactionManager,
        authorizationService
      ),
      views = ClubViewAssembler(
        clubTables = tables,
        authorizationService = authorizationService
      )
    )

  def context(
      core: ClubModuleCore,
      tournamentService: TournamentApplicationService,
      tournamentViews: TournamentViewAssembler
  ): ClubModuleContext =
    ClubModuleContext(
      tables = core.tables,
      service = core.service,
      views = core.views,
      tournamentService = tournamentService,
      tournamentViews = tournamentViews
    )
