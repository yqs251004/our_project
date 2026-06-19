package riichinexus.microservices.tournament.appeal.domain.model

/** AppealPriority 表示后端领域中的申诉优先级 状态。 */

enum AppealPriority:
  case Low
  case Normal
  case High
  case Critical