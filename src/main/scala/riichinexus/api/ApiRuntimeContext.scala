package riichinexus.api

import riichinexus.bootstrap.ApplicationContext
import riichinexus.api.runtime.ApiExecutionContext
import riichinexus.system.instrumentation.PerformanceDiagnosticsService

final case class ApiRuntimeContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    performanceDiagnosticsService: PerformanceDiagnosticsService
)

object ApiRuntimeContext:
  def fromApplication(
      app: ApplicationContext,
      config: ApiServerConfig
  ): ApiRuntimeContext =
    fromApplication(
      app = app,
      storageLabel = config.storageLabel,
      corsAllowOrigin = config.corsAllowOrigin
    )

  def fromApplication(
      app: ApplicationContext,
      storageLabel: String,
      corsAllowOrigin: String = "*"
  ): ApiRuntimeContext =
    ApiRuntimeContext(
      executionContext = ApiExecutionContext(
        connectionFactory = app.connectionFactory,
        authModule = app.authModule,
        playerModule = app.playerModule,
        clubModule = app.clubModule,
        opsAnalyticsModule = app.opsAnalyticsModule,
        tournamentModule = app.tournamentModule,
        platformAdminModule = app.platformAdminModule,
        tournamentAppealModule = app.tournamentAppealModule,
        authorizationService = app.authorizationService,
        storageLabel = storageLabel
      ),
      corsAllowOrigin = corsAllowOrigin,
      performanceDiagnosticsService = app.performanceDiagnosticsService
    )
