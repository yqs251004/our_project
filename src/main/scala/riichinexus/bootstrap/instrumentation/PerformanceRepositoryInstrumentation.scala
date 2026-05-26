package riichinexus.bootstrap.instrumentation

import riichinexus.bootstrap.ApplicationRepositoryContext

object PerformanceRepositoryInstrumentation:
  def instrument(
      repositories: ApplicationRepositoryContext,
      diagnostics: PerformanceDiagnosticsService
  ): ApplicationRepositoryContext =
    repositories
