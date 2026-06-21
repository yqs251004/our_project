package riichinexus.microservices.opsanalytics.objects

/** 高级统计重算任务在队列中的处理状态。
  *
  * 状态覆盖等待、处理中、完成、可重试失败和死信，供调度器与运维面板共同判断任务是否还能继续执行。
  */
enum AdvancedStatsRecomputeTaskStatus:
  case Pending
  case Processing
  case Completed
  case Failed
  case DeadLetter

object AdvancedStatsRecomputeTaskStatus:
  def toString(status: AdvancedStatsRecomputeTaskStatus): String =
    status match
      case AdvancedStatsRecomputeTaskStatus.Pending    => "Pending"
      case AdvancedStatsRecomputeTaskStatus.Processing => "Processing"
      case AdvancedStatsRecomputeTaskStatus.Completed  => "Completed"
      case AdvancedStatsRecomputeTaskStatus.Failed     => "Failed"
      case AdvancedStatsRecomputeTaskStatus.DeadLetter => "DeadLetter"

  def fromString(value: String): Either[String, AdvancedStatsRecomputeTaskStatus] =
    value.trim match
      case "Pending"    => Right(AdvancedStatsRecomputeTaskStatus.Pending)
      case "Processing" => Right(AdvancedStatsRecomputeTaskStatus.Processing)
      case "Completed"  => Right(AdvancedStatsRecomputeTaskStatus.Completed)
      case "Failed"     => Right(AdvancedStatsRecomputeTaskStatus.Failed)
      case "DeadLetter" => Right(AdvancedStatsRecomputeTaskStatus.DeadLetter)
      case other        => Left(s"Unsupported AdvancedStatsRecomputeTaskStatus value: $other")
