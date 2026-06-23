package riichinexus.microservices.opsanalytics.objects

/** 高级统计重算任务的稳定标识符。
  *
  * 任务 ID 独立于玩家、俱乐部和对局 ID，便于队列重试、日志追踪和运维面板定位。
  */
final case class AdvancedStatsRecomputeTaskId(value: String)
