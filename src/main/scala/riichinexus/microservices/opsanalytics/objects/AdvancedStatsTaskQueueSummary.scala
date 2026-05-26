package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

final case class AdvancedStatsTaskQueueSummary(
    asOf: Instant,
    runnablePendingCount: Int,
    scheduledRetryCount: Int,
    processingCount: Int,
    completedCount: Int,
    failedCount: Int,
    deadLetterCount: Int,
    oldestRunnableRequestedAt: Option[Instant],
    nextScheduledRetryAt: Option[Instant],
    newestCompletedAt: Option[Instant]
) derives CanEqual
