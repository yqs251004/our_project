package riichinexus.microservices.tournament.appeal.domain.model

enum AppealStatus derives CanEqual:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated
