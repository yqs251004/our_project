package riichinexus.api

import riichinexus.api.runtime.ApiExecutionContext
import riichinexus.system.instrumentation.PerformanceDiagnosticsService

final case class ApiRuntimeContext(
    executionContext: ApiExecutionContext,
    corsAllowOrigin: String,
    performanceDiagnosticsService: PerformanceDiagnosticsService
)
