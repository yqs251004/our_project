package riichinexus.microservices.tournament.appeal.objects

/** AppealDecisionType 枚举申诉裁定类型 可使用的公开取值。 */

enum AppealDecisionType:
  case Resolve
  case Reject
  case Escalate

object AppealDecisionType:
  def toString(decision: AppealDecisionType): String =
    decision.toString

  def fromString(value: String): AppealDecisionType =
    AppealDecisionType.valueOf(value)
