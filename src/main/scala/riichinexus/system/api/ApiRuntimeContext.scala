package riichinexus.system.api

import riichinexus.system.api.runtime.ApiExecutionContext
import riichinexus.system.postgres.JdbcConnectionFactory
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.paifumanagement.functions.TournamentPaifuArchiveService
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementCoordinator
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentStageCompletionCoordinator
import riichinexus.system.instrumentation.PerformanceDiagnosticsService

final case class ApiRuntimeContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    performanceDiagnosticsService: PerformanceDiagnosticsService
)

object ApiRuntimeContext:

  def fromConnectionFactory(
      connectionFactory: JdbcConnectionFactory,
      config: ApiServerConfig
  ): ApiRuntimeContext =
    fromConnectionFactory(
      connectionFactory = connectionFactory,
      storageLabel = config.storageLabel,
      corsAllowOrigin = config.corsAllowOrigin
    )

  def fromConnectionFactory(
      connectionFactory: JdbcConnectionFactory,
      storageLabel: String,
      corsAllowOrigin: String = "*"
  ): ApiRuntimeContext =
    val authorizationService = AuthorizationPolicyFunctions.strict
    ApiRuntimeContext(
      executionContext = ApiExecutionContext(
        connectionFactory = connectionFactory,
        tournamentPaifuArchiveService = TournamentPaifuArchiveService(authorizationService),
        tournamentSettlementCoordinator = TournamentSettlementCoordinator(authorizationService),
        tournamentStageCompletionCoordinator = TournamentStageCompletionCoordinator(authorizationService),
        tournamentAppealService = AppealApplicationService(authorizationService),
        storageLabel = storageLabel
      ),
      corsAllowOrigin = corsAllowOrigin,
      performanceDiagnosticsService = PerformanceDiagnosticsService()
    )
