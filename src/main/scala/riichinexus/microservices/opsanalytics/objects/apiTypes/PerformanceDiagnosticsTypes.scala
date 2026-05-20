package riichinexus.microservices.opsanalytics.objects.apiTypes

import java.time.Instant

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
) derives CanEqual

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW

final case class PerformanceMetricEntry(
    key: String,
    count: Long,
    totalMillis: Double,
    averageMillis: Double,
    maxMillis: Double,
    lastMillis: Double,
    lastUpdatedAt: Instant,
    statusCounts: Vector[PerformanceStatusCount] = Vector.empty
) derives CanEqual

object PerformanceMetricEntry:
  given ReadWriter[PerformanceMetricEntry] = macroRW

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
