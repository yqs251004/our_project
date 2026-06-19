package riichinexus.microservices.opsanalytics.objects

/** AdvancedStatsRecomputeTaskStatus 枚举高级统计重算任务状态 可使用的公开取值。 */

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
