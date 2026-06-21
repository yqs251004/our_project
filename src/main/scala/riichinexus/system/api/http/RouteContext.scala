package riichinexus.system.api.http

import riichinexus.system.api.runtime.ApiExecutionContext
import riichinexus.system.realtime.domain.RealtimeEventBus

/** HTTP 路由层共享的请求处理上下文。 */
final case class RouteContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    realtimeEventBus: RealtimeEventBus
)

object RouteContext:

  def storageLabel(routeContext: RouteContext): String =
    routeContext.executionContext.storageLabel
