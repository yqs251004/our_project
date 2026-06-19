package riichinexus.microservices.tournament.appeal.domain.model

/** AppealStatus 表示后端领域中的申诉状态 状态。 */

enum AppealStatus:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated