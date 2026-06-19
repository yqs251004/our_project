package riichinexus.microservices.opsanalytics.router
import riichinexus.system.api.RegisteredAPIMessage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.{OpsAnalyticsAdvancedStatsSummaryAPIMessage, OpsAnalyticsClubAdvancedStatsAPIMessage, OpsAnalyticsClubDashboardAPIMessage, OpsAnalyticsListAdvancedStatsTasksAPIMessage, OpsAnalyticsPlayerAdvancedStatsAPIMessage, OpsAnalyticsPlayerDashboardAPIMessage, OpsAnalyticsProcessAdvancedStatsAPIMessage, OpsAnalyticsRecomputeAdvancedStatsAPIMessage}
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, AdvancedStatsRecomputeTask, AdvancedStatsTaskQueueSummary, Dashboard}
import riichinexus.system.objects.PagedResponse

object OpsAnalyticsAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[OpsAnalyticsPlayerDashboardAPIMessage, Dashboard],
      RegisteredAPIMessage.api[OpsAnalyticsClubDashboardAPIMessage, Dashboard],
      RegisteredAPIMessage.api[OpsAnalyticsPlayerAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessage.api[OpsAnalyticsClubAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessage.api[OpsAnalyticsListAdvancedStatsTasksAPIMessage, PagedResponse[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessage.api[OpsAnalyticsAdvancedStatsSummaryAPIMessage, AdvancedStatsTaskQueueSummary],
      RegisteredAPIMessage.accepted[OpsAnalyticsRecomputeAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessage.api[OpsAnalyticsProcessAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]]
    )
