package riichinexus.microservices.opsanalytics.domain.functions

import riichinexus.microservices.opsanalytics.domain.model.OpsAnalyticsIdPrefix
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsRecomputeTaskId

import java.util.UUID

/** OpsAnalyticsIdGenerator 负责生成OpsAnalytics标识符生成器 相关的领域标识符。 */

private[opsanalytics] object OpsAnalyticsIdGenerator:
  private def nextId(prefix: OpsAnalyticsIdPrefix): String =
    s"${OpsAnalyticsIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def advancedStatsRecomputeTaskId(): AdvancedStatsRecomputeTaskId =
    AdvancedStatsRecomputeTaskId(nextId(OpsAnalyticsIdPrefix.AdvancedStatsRecomputeTask))
