package riichinexus.api

import riichinexus.bootstrap.ApplicationContext
import riichinexus.bootstrap.instrumentation.PerformanceDiagnosticsService
import riichinexus.api.http.RouteContext

final case class ApiRuntimeContext(
    routeContext: RouteContext,
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
      routeContext = RouteContext(
        authModule = app.authModule,
        playerModule = app.playerModule,
        clubModule = app.clubModule,
        dictionaryModule = app.dictionaryModule,
        publicQueryModule = app.publicQueryModule,
        opsAnalyticsModule = app.opsAnalyticsModule,
        tournamentModule = app.tournamentModule,
        platformAdminModule = app.platformAdminModule,
        tournamentAppealModule = app.tournamentAppealModule,
        authorizationService = app.authorizationService,
        storageLabel = storageLabel,
        corsAllowOrigin = corsAllowOrigin
      ),
      performanceDiagnosticsService = app.opsAnalyticsModule.performanceDiagnosticsService
    )
