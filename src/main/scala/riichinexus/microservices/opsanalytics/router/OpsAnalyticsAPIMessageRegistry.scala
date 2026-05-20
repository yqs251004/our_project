package riichinexus.microservices.opsanalytics.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.*
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.opsanalytics.objects.apiTypes.*
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.system.objects.PagedResponse

object OpsAnalyticsAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[OpsAnalyticsPlayerDashboardAPIMessage, Dashboard],
      RegisteredAPIMessage.api[OpsAnalyticsClubDashboardAPIMessage, Dashboard],
      RegisteredAPIMessage.api[OpsAnalyticsPlayerAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessage.api[OpsAnalyticsClubAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessage.api[OpsAnalyticsListAuditsAPIMessage, PagedResponse[AuditEventEntry]],
      RegisteredAPIMessage.api[OpsAnalyticsListAggregateAuditsAPIMessage, PagedResponse[AuditEventEntry]],
      RegisteredAPIMessage.api[OpsAnalyticsListAdvancedStatsTasksAPIMessage, PagedResponse[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessage.api[OpsAnalyticsAdvancedStatsSummaryAPIMessage, AdvancedStatsTaskQueueSummary],
      RegisteredAPIMessage.accepted[OpsAnalyticsRecomputeAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessage.api[OpsAnalyticsProcessAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessage.api[OpsAnalyticsPerformanceSummaryAPIMessage, PerformanceDiagnosticsSnapshot],
      RegisteredAPIMessage.api[OpsAnalyticsDomainEventsSummaryAPIMessage, DomainEventBusSummary],
      RegisteredAPIMessage.api[OpsAnalyticsListDomainEventOutboxAPIMessage, PagedResponse[DomainEventOutboxRecord]],
      RegisteredAPIMessage.api[OpsAnalyticsReplayDomainEventOutboxAPIMessage, DomainEventOutboxBatchOperationResult],
      RegisteredAPIMessage.api[OpsAnalyticsAcknowledgeDomainEventOutboxAPIMessage, DomainEventOutboxBatchOperationResult],
      RegisteredAPIMessage.api[OpsAnalyticsQuarantineDomainEventOutboxAPIMessage, DomainEventOutboxBatchOperationResult],
      RegisteredAPIMessage.api[OpsAnalyticsDomainEventOutboxHistoryAPIMessage, DomainEventOutboxHistoryView],
      RegisteredAPIMessage.api[OpsAnalyticsReplayDomainEventOutboxRecordAPIMessage, DomainEventOutboxRecord],
      RegisteredAPIMessage.api[OpsAnalyticsAcknowledgeDomainEventOutboxRecordAPIMessage, DomainEventOutboxRecord],
      RegisteredAPIMessage.api[OpsAnalyticsQuarantineDomainEventOutboxRecordAPIMessage, DomainEventOutboxRecord],
      RegisteredAPIMessage.api[OpsAnalyticsListDomainEventSubscribersAPIMessage, PagedResponse[DomainEventSubscriberStatus]],
      RegisteredAPIMessage.api[OpsAnalyticsListDomainEventSubscriberPartitionsAPIMessage, PagedResponse[DomainEventSubscriberPartitionStatus]],
      RegisteredAPIMessage.api[OpsAnalyticsListEventCascadeRecordsAPIMessage, PagedResponse[EventCascadeRecord]]
    )
