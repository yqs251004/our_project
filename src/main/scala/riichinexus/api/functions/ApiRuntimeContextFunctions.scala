package riichinexus.api.functions

import riichinexus.api.{ApiRuntimeContext, ApiServerConfig}
import riichinexus.api.runtime.ApiExecutionContext
import riichinexus.bootstrap.ApplicationContext

object ApiRuntimeContextFunctions:

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
