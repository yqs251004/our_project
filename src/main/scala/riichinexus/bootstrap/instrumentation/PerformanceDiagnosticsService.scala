package riichinexus.bootstrap.instrumentation

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import riichinexus.microservices.opsanalytics.objects.apiTypes.*

final class PerformanceDiagnosticsService(
    val startedAt: Instant = Instant.now()
):
  private final case class MetricAccumulator(
      count: Long = 0L,
      totalNanos: Long = 0L,
      maxNanos: Long = 0L,
      lastNanos: Long = 0L,
      lastUpdatedAt: Instant = startedAt,
      statusCounts: Map[Int, Long] = Map.empty
  ):

    def record(durationNanos: Long, occurredAt: Instant, statusCode: Option[Int]): MetricAccumulator =
      copy(
        count = count + 1L,
        totalNanos = totalNanos + durationNanos,
        maxNanos = math.max(maxNanos, durationNanos),
        lastNanos = durationNanos,
        lastUpdatedAt = occurredAt,
        statusCounts = statusCode.fold(statusCounts)(code =>
          statusCounts.updated(code, statusCounts.getOrElse(code, 0L) + 1L)
        )
      )

  private val requestMetrics = AtomicReference(Map.empty[String, MetricAccumulator])
  private val repositoryMetrics = AtomicReference(Map.empty[String, MetricAccumulator])

  def recordRequest(
      method: String,
      path: String,
      statusCode: Int,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit =
    recordMetric(requestMetrics, s"$method $path", durationNanos, occurredAt, Some(statusCode))

  def recordRepositoryCall(
      repository: String,
      operation: String,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit =
    recordMetric(repositoryMetrics, s"$repository.$operation", durationNanos, occurredAt, None)

  def snapshot(
      limit: Int = 15,
      generatedAt: Instant = Instant.now()
  ): PerformanceDiagnosticsSnapshot =
    val safeLimit = math.max(1, limit)
    val requestEntries = requestMetrics.get().toVector.map(toEntry)
    val repositoryEntries = repositoryMetrics.get().toVector.map(toEntry)

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

  @tailrec
  private def recordMetric(
      metrics: AtomicReference[Map[String, MetricAccumulator]],
      key: String,
      durationNanos: Long,
      occurredAt: Instant,
      statusCode: Option[Int]
  ): Unit =
    val current = metrics.get()
    val nextMetric = current.getOrElse(key, MetricAccumulator()).record(durationNanos, occurredAt, statusCode)
    val next = current.updated(key, nextMetric)
    if !metrics.compareAndSet(current, next) then
      recordMetric(metrics, key, durationNanos, occurredAt, statusCode)

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

