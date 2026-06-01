package riichinexus.microservices.opsanalytics.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.*
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.system.objects.PagedResponse

object OpsAnalyticsAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.api[OpsAnalyticsPlayerDashboardAPIMessage, Dashboard],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsClubDashboardAPIMessage, Dashboard],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsPlayerAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsClubAdvancedStatsAPIMessage, AdvancedStatsBoard],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsListAdvancedStatsTasksAPIMessage, PagedResponse[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsAdvancedStatsSummaryAPIMessage, AdvancedStatsTaskQueueSummary],
      RegisteredAPIMessageFunctions.accepted[OpsAnalyticsRecomputeAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]],
      RegisteredAPIMessageFunctions.api[OpsAnalyticsProcessAdvancedStatsAPIMessage, Vector[AdvancedStatsRecomputeTask]]
    )
