package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** AdvancedStatsTaskQueueSummary 表示前后端共享的高级统计任务队列摘要 数据结构，包含asOf、runnablePendingCount、scheduledRetryCount、processingCount、completedCount、failedCount等。 */

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
)
