package riichinexus.microservices.auth

import riichinexus.application.ports.TransactionManager
import riichinexus.bootstrap.{ApplicationRepositoryContext, AuthModuleContext}
import riichinexus.microservices.auth.api.{AuthApplicationService, GuestSessionApplicationService}
import riichinexus.microservices.auth.tables.AuthTables
import riichinexus.microservices.player.api.PlayerApplicationService

object AuthModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      transactionManager: TransactionManager,
      playerService: PlayerApplicationService
  ): AuthModuleContext =
    AuthModuleContext(
      tables = AuthTables(
        playerRepository = repositories.playerRepository,
        guestSessionRepository = repositories.guestSessionRepository
      ),
      authService = AuthApplicationService(
        playerService = playerService,
        playerRepository = repositories.playerRepository,
        accountCredentialRepository = repositories.accountCredentialRepository,
        authenticatedSessionRepository = repositories.authenticatedSessionRepository,
        transactionManager = transactionManager
      ),
      guestSessionService = GuestSessionApplicationService(
        repositories.playerRepository,
        repositories.guestSessionRepository,
        repositories.clubRepository,
        repositories.auditEventRepository,
        transactionManager
      )
    )
