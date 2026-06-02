package riichinexus.system.api.runtime

import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.paifumanagement.functions.TournamentPaifuArchiveService
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementCoordinator
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentStageCompletionCoordinator

final case class ApiPlanSupport(
    executionContext: ApiExecutionContext,
    tournamentPaifuArchiveService: TournamentPaifuArchiveService,
    tournamentSettlementCoordinator: TournamentSettlementCoordinator,
    tournamentStageCompletionCoordinator: TournamentStageCompletionCoordinator,
    tournamentAppealService: AppealApplicationService
)

object ApiPlanSupport:

  def fromExecutionContext(executionContext: ApiExecutionContext): ApiPlanSupport =
    ApiPlanSupport(
      executionContext = executionContext,
      tournamentPaifuArchiveService = executionContext.tournamentPaifuArchiveService,
      tournamentSettlementCoordinator = executionContext.tournamentSettlementCoordinator,
      tournamentStageCompletionCoordinator = executionContext.tournamentStageCompletionCoordinator,
      tournamentAppealService = executionContext.tournamentAppealService
    )
