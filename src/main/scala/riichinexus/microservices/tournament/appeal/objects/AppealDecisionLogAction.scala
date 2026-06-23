package riichinexus.microservices.tournament.appeal.objects

/** 申诉处理日志中的结构化动作类型。
  *
  * 该枚举只描述工单流转动作本身，动作附带的目标玩家、优先级或裁定文本由日志字段单独承载。
  */
enum AppealDecisionLogAction:
  case Assigned
  case Unassigned
  case Triaged
  case UnderReview
  case Resolved
  case Rejected
  case Escalated
  case Reopened

object AppealDecisionLogAction:
  def toString(action: AppealDecisionLogAction): String =
    action.toString

  def fromString(value: String): AppealDecisionLogAction =
    AppealDecisionLogAction.valueOf(value)
