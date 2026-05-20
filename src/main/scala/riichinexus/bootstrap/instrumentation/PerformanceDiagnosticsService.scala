package riichinexus.bootstrap.instrumentation

import java.time.Instant

import scala.collection.mutable

import riichinexus.microservices.opsanalytics.objects.apiTypes.*

final class PerformanceDiagnosticsService(
    val startedAt: Instant = Instant.now()
):
  private final class MetricAccumulator:
    var count: Long = 0L
    var totalNanos: Long = 0L
    var maxNanos: Long = 0L
    var lastNanos: Long = 0L
    var lastUpdatedAt: Instant = startedAt
    val statusCounts: mutable.Map[Int, Long] = mutable.Map.empty

    def record(durationNanos: Long, occurredAt: Instant, statusCode: Option[Int]): Unit =
      count += 1
      totalNanos += durationNanos
      maxNanos = math.max(maxNanos, durationNanos)
      lastNanos = durationNanos
      lastUpdatedAt = occurredAt
      statusCode.foreach(code =>
        statusCounts.update(code, statusCounts.getOrElse(code, 0L) + 1L)
      )

  private val requestMetrics = mutable.Map.empty[String, MetricAccumulator]
  private val repositoryMetrics = mutable.Map.empty[String, MetricAccumulator]

  def recordRequest(
      method: String,
      path: String,
      statusCode: Int,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit = synchronized {
    requestMetrics
      .getOrElseUpdate(s"$method $path", MetricAccumulator())
      .record(durationNanos, occurredAt, Some(statusCode))
  }

  def recordRepositoryCall(
      repository: String,
      operation: String,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit = synchronized {
    repositoryMetrics
      .getOrElseUpdate(s"$repository.$operation", MetricAccumulator())
      .record(durationNanos, occurredAt, None)
  }

  def snapshot(
      limit: Int = 15,
      generatedAt: Instant = Instant.now()
  ): PerformanceDiagnosticsSnapshot = synchronized {
    val safeLimit = math.max(1, limit)
    val requestEntries = requestMetrics.toVector.map(toEntry)
    val repositoryEntries = repositoryMetrics.toVector.map(toEntry)

    PerformanceDiagnosticsSnapshot(
      startedAt = startedAt,
      generatedAt = generatedAt,
      totalRequestCount = requestEntries.map(_.count).sum,
      totalRepositoryCallCount = repositoryEntries.map(_.count).sum,
      slowestRequests = requestEntries.sortBy(entry => (-entry.averageMillis, -entry.maxMillis, entry.key)).take(safeLimit),
      busiestRequests = requestEntries.sortBy(entry => (-entry.totalMillis, -entry.count, entry.key)).take(safeLimit),
      slowestRepositoryCalls = repositoryEntries.sortBy(entry => (-entry.averageMillis, -entry.maxMillis, entry.key)).take(safeLimit),
      busiestRepositoryCalls = repositoryEntries.sortBy(entry => (-entry.totalMillis, -entry.count, entry.key)).take(safeLimit)
    )
  }

  private def toEntry(raw: (String, MetricAccumulator)): PerformanceMetricEntry =
    val (key, metric) = raw
    val nanosPerMillis = 1000000.0
    PerformanceMetricEntry(
      key = key,
      count = metric.count,
      totalMillis = metric.totalNanos / nanosPerMillis,
      averageMillis =
        if metric.count <= 0 then 0.0
        else metric.totalNanos.toDouble / metric.count.toDouble / nanosPerMillis,
      maxMillis = metric.maxNanos / nanosPerMillis,
      lastMillis = metric.lastNanos / nanosPerMillis,
      lastUpdatedAt = metric.lastUpdatedAt,
      statusCounts = metric.statusCounts.toVector
        .sortBy(_._1)
        .map { case (statusCode, count) =>
          PerformanceStatusCount(statusCode = statusCode, count = count)
        }
    )

