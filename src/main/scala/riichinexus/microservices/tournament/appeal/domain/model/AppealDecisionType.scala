package riichinexus.microservices.tournament.appeal.domain.model

enum AppealDecisionType derives CanEqual:
  case Resolve
  case Reject
  case Escalate
