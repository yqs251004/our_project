package riichinexus.microservices.opsanalytics.domain.model

/** 运营分析微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[opsanalytics] enum OpsAnalyticsIdPrefix:
  case AdvancedStatsRecomputeTask

object OpsAnalyticsIdPrefix:
  def toString(prefix: OpsAnalyticsIdPrefix): String =
    prefix match
      case OpsAnalyticsIdPrefix.AdvancedStatsRecomputeTask => "advanced-stats-task"
