package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.domain.model.{AdvancedStatsRecomputeTask, AdvancedStatsTaskQueueSummary}

object AdvancedStatsResponses:
  type TaskResponse = AdvancedStatsRecomputeTask
  type TaskQueueSummaryResponse = AdvancedStatsTaskQueueSummary
