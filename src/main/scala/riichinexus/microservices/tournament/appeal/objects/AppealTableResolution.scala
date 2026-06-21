package riichinexus.microservices.tournament.appeal.objects

/** 申诉裁定后对牌桌状态采取的处理方案。
  *
  * 这些取值把人工裁定与牌桌运行状态衔接起来，例如恢复旧状态、封存桌面、继续计分、恢复对局或强制重置。
  */
enum AppealTableResolution:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset

object AppealTableResolution:
  def toString(resolution: AppealTableResolution): String =
    resolution.toString

  def fromString(value: String): AppealTableResolution =
    AppealTableResolution.valueOf(value)
