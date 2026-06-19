package riichinexus.microservices.tournament.appeal.objects

/** AppealTableResolution 枚举申诉牌桌处理结果 可使用的公开取值。 */

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
