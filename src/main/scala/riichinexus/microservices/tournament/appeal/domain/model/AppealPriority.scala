package riichinexus.microservices.tournament.appeal.domain.model

enum AppealPriority derives CanEqual:
  case Low
  case Normal
  case High
  case Critical
