package riichinexus.microservices.tournament.appeal.domain.model

enum AppealStatus:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated
