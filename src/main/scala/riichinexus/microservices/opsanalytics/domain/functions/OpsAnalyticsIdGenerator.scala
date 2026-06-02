package riichinexus.microservices.opsanalytics.domain.functions

import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId

import java.util.UUID

object OpsAnalyticsIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def advancedStatsRecomputeTaskId(): AdvancedStatsRecomputeTaskId =
    AdvancedStatsRecomputeTaskId(nextId("advanced-stats-task"))
