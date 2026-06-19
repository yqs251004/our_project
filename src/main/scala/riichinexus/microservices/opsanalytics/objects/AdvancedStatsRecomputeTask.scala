package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId

/** AdvancedStatsRecomputeTask 表示前后端共享的高级统计重算任务 数据结构，包含 ID、owner、reason、calculatorVersion、requestedAt、状态等。 */

final case class AdvancedStatsRecomputeTask(
    id: AdvancedStatsRecomputeTaskId,
    owner: DashboardOwner,
    reason: String,
    calculatorVersion: Int,
    requestedAt: Instant,
    status: AdvancedStatsRecomputeTaskStatus,
    attempts: Int = 0,
    lastError: Option[String] = None,
    lastMatchRecordId: Option[MatchRecordId] = None,
    nextAttemptAt: Option[Instant] = None,
    startedAt: Option[Instant] = None,
    completedAt: Option[Instant] = None,
    deadLetteredAt: Option[Instant] = None,
    version: Int = 0
)
