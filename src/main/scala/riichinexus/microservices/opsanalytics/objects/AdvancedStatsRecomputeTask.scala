package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

import riichinexus.domain.model.{AdvancedStatsRecomputeTaskId, MatchRecordId}

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
) derives CanEqual
