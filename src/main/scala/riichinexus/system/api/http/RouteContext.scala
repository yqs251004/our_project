package riichinexus.system.api.http

import riichinexus.system.api.runtime.ApiExecutionContext
import riichinexus.system.realtime.domain.RealtimeEventBus

final case class RouteContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    realtimeEventBus: RealtimeEventBus
)

object RouteContext:

  def storageLabel(routeContext: RouteContext): String =
    routeContext.executionContext.storageLabel
