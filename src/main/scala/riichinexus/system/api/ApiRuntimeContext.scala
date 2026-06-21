package riichinexus.system.api

import riichinexus.system.api.runtime.ApiExecutionContext
import riichinexus.system.postgres.JdbcConnectionFactory
import riichinexus.system.instrumentation.PerformanceDiagnosticsService
import riichinexus.system.realtime.domain.RealtimeEventBus

/** API 运行期共享的应用级依赖。
  *
  * 该上下文汇总执行上下文、CORS 配置、性能诊断服务和实时事件总线，是 HTTP 路由构建和消息执行的根依赖。
  */
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
