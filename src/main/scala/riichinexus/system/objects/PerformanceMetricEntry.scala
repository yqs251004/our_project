package riichinexus.system.objects

import java.time.Instant

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
