package riichinexus.microservices.opsanalytics

import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsService

object OpsAnalyticsDiagnosticsAssembly:
  def build(): PerformanceDiagnosticsService =
    PerformanceDiagnosticsService()
