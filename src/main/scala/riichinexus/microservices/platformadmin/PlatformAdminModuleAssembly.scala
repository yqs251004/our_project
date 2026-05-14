package riichinexus.microservices.platformadmin

import riichinexus.application.ports.{DomainEventBus, TransactionManager}
import riichinexus.bootstrap.{ApplicationRepositoryContext, PlatformAdminModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.platformadmin.api.SuperAdminService
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables

object PlatformAdminModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService
  ): PlatformAdminModuleContext =
    PlatformAdminModuleContext(
      tables = PlatformAdminTables(
        playerRepository = repositories.playerRepository,
        clubRepository = repositories.clubRepository
      ),
      service = SuperAdminService(
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      )
    )
