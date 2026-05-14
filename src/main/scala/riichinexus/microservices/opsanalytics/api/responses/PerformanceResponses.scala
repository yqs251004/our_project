package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsSnapshot

object PerformanceResponses:
  type PerformanceSummaryResponse = PerformanceDiagnosticsSnapshot

  export PerformanceResponseCodecs.given
