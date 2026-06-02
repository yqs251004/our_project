package riichinexus.system.api

import riichinexus.system.api.runtime.ApiExecutionContext
import riichinexus.system.postgres.JdbcConnectionFactory
import riichinexus.system.instrumentation.PerformanceDiagnosticsService
import riichinexus.system.realtime.domain.RealtimeEventBus

final case class ApiRuntimeContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    performanceDiagnosticsService: PerformanceDiagnosticsService,
    realtimeEventBus: RealtimeEventBus
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
    ApiRuntimeContext(
      executionContext = ApiExecutionContext(
        connectionFactory = connectionFactory,
        storageLabel = storageLabel
      ),
      corsAllowOrigin = corsAllowOrigin,
      performanceDiagnosticsService = PerformanceDiagnosticsService(),
      realtimeEventBus = RealtimeEventBus.empty
    )
