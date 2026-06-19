package riichinexus.microservices.tournament.appeal.domain.model

/** AppealDecisionType 表示后端领域中的申诉裁定类型 状态。 */

enum AppealDecisionType:
  case Resolve
  case Reject
  case Escalate