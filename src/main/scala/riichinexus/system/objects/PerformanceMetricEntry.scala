package riichinexus.system.objects

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

final case class PerformanceMetricEntry(
    key: String,
    count: Long,
    totalMillis: Double,
    averageMillis: Double,
    maxMillis: Double,
    lastMillis: Double,
    lastUpdatedAt: Instant,
    statusCounts: Vector[PerformanceStatusCount] = Vector.empty
)

object PerformanceMetricEntry:
  given ReadWriter[PerformanceMetricEntry] = macroRW
