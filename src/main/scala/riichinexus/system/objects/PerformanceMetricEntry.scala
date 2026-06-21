package riichinexus.system.objects

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 单个请求或仓储调用的性能指标汇总项。 */
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
