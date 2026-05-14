package riichinexus.microservices.player

import riichinexus.application.ports.TransactionManager
import riichinexus.bootstrap.{ApplicationRepositoryContext, PlayerModuleContext}
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.player.tables.PlayerTables

final case class PlayerModuleBuild(
    context: PlayerModuleContext,
    service: PlayerApplicationService
)

object PlayerModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      transactionManager: TransactionManager
  ): PlayerModuleBuild =
    val service = PlayerApplicationService(
      repositories.playerRepository,
      repositories.dashboardRepository,
      transactionManager
    )
    PlayerModuleBuild(
      context = PlayerModuleContext(
        tables = PlayerTables(
          playerRepository = repositories.playerRepository
        ),
        service = service
      ),
      service = service
    )
