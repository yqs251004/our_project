package riichinexus.api.http

import riichinexus.api.runtime.ApiExecutionContext

final case class RouteContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String
)
