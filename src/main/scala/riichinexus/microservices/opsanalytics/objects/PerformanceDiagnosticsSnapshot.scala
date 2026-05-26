package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.infrastructure.json.JsonCodecs.given
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
) derives CanEqual

object PerformanceDiagnosticsSnapshot:
  given ReadWriter[PerformanceDiagnosticsSnapshot] = macroRW
