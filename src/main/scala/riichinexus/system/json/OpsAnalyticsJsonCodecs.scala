package riichinexus.system.json

import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBackfillMode, AdvancedStatsBoard, AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus, AdvancedStatsTaskQueueSummary, Dashboard, DashboardOwner}
import riichinexus.system.json.JsonCodecSupport.eitherStringEnumReadWriter
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

object OpsAnalyticsJsonCodecs:
  given ReadWriter[DashboardOwner] =
    eitherStringEnumReadWriter(
      DashboardOwner.fromString,
      DashboardOwner.toString
    )
  given ReadWriter[Dashboard] = macroRW
  given ReadWriter[AdvancedStatsBoard] = macroRW
  given ReadWriter[AdvancedStatsRecomputeTaskStatus] =
    eitherStringEnumReadWriter(
      AdvancedStatsRecomputeTaskStatus.fromString,
      AdvancedStatsRecomputeTaskStatus.toString
    )
  given ReadWriter[AdvancedStatsBackfillMode] =
    eitherStringEnumReadWriter(
      AdvancedStatsBackfillMode.fromString,
      AdvancedStatsBackfillMode.toString
    )
  given ReadWriter[AdvancedStatsRecomputeTask] = macroRW
  given ReadWriter[AdvancedStatsTaskQueueSummary] = macroRW
