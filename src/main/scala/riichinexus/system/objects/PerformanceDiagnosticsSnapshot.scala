package riichinexus.system.objects

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class PerformanceDiagnosticsSnapshot(
    startedAt: Instant,
    generatedAt: Instant,
    totalRequestCount: Long,
    totalRepositoryCallCount: Long,
    slowestRequests: Vector[PerformanceMetricEntry],
    busiestRequests: Vector[PerformanceMetricEntry],
    slowestRepositoryCalls: Vector[PerformanceMetricEntry],
    busiestRepositoryCalls: Vector[PerformanceMetricEntry]
)

object PerformanceDiagnosticsSnapshot:
  given ReadWriter[PerformanceDiagnosticsSnapshot] = macroRW
