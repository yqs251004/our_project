package riichinexus.system.api.http

import riichinexus.system.api.runtime.ApiExecutionContext

final case class RouteContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String
)

object RouteContext:

  def storageLabel(routeContext: RouteContext): String =
    routeContext.executionContext.storageLabel
