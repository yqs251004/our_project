package riichinexus.microservices.tournament.appeal

import riichinexus.application.ports.{DomainEventBus, TransactionManager}
import riichinexus.bootstrap.{ApplicationRepositoryContext, TournamentAppealModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.tournament.api.KnockoutStageCoordinator
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables

object TournamentAppealModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      knockoutStageCoordinator: KnockoutStageCoordinator,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService
  ): TournamentAppealModuleContext =
    TournamentAppealModuleContext(
      tables = TournamentAppealTables(
        appealTicketRepository = repositories.appealTicketRepository
      ),
      service = AppealApplicationService(
        repositories.appealTicketRepository,
        repositories.tableRepository,
        repositories.playerRepository,
        knockoutStageCoordinator,
        repositories.auditEventRepository,
        eventBus,
        transactionManager,
        authorizationService
      )
    )
