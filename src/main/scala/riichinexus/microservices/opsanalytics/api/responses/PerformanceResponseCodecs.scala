package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.*
import upickle.default.*

object PerformanceResponseCodecs:
  given ReadWriter[PerformanceStatusCount] = macroRW
  given ReadWriter[PerformanceMetricEntry] = macroRW
  given ReadWriter[PerformanceDiagnosticsSnapshot] = macroRW
