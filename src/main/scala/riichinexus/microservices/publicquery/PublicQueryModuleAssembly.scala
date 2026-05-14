package riichinexus.microservices.publicquery

import riichinexus.bootstrap.{ApplicationRepositoryContext, PublicQueryModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.club.api.ClubViewAssembler
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.api.TournamentViewAssembler

final case class PublicQueryModuleBuild(
    context: PublicQueryModuleContext,
    service: PublicQueryService
)

object PublicQueryModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      authorizationService: AuthorizationService,
      clubViews: ClubViewAssembler,
      tournamentViews: TournamentViewAssembler
  ): PublicQueryModuleBuild =
    val tables = PublicQueryTables(
      tournamentRepository = repositories.tournamentRepository,
      tableRepository = repositories.tableRepository,
      playerRepository = repositories.playerRepository,
      clubRepository = repositories.clubRepository,
      globalDictionaryRepository = repositories.globalDictionaryRepository
    )
    val service = PublicQueryService(
      repositories.tournamentRepository,
      repositories.tableRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.globalDictionaryRepository,
      authorizationService
    )
    PublicQueryModuleBuild(
      context = PublicQueryModuleContext(
        tables = tables,
        service = service,
        clubViews = clubViews,
        tournamentViews = tournamentViews
      ),
      service = service
    )
