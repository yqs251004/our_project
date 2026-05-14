package riichinexus.microservices.opsanalytics.api

object PerformanceApi:

  def snapshot(
      service: PerformanceDiagnosticsService,
      limit: Int
  ): PerformanceDiagnosticsSnapshot =
    service.snapshot(limit = math.min(limit, 100))
