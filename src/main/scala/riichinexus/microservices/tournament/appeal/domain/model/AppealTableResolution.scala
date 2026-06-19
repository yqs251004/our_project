package riichinexus.microservices.tournament.appeal.domain.model

/** AppealTableResolution 表示后端领域中的申诉牌桌处理结果 状态。 */

enum AppealTableResolution:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset