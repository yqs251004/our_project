package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** 高级统计任务队列的运维摘要。
  *
  * 它按状态汇总当前积压、重试、处理中、失败和死信任务，并暴露最早可运行任务与下一次重试时间，方便后台判断队列健康度。
  */
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
