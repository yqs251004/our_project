package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsRecomputeTaskId

/** 后台队列中一条高级统计重算任务。
  *
  * 任务记录目标看板、计算器版本、请求原因、尝试次数、最近处理到的对局和生命周期时间点，支持失败重试与死信排查。
  */
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
