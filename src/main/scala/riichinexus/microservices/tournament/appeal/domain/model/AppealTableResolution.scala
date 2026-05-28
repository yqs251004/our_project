package riichinexus.microservices.tournament.appeal.domain.model

enum AppealTableResolution derives CanEqual:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset
