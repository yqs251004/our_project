package riichinexus.microservices.tournament.appeal.objects

/** 审核员对申诉作出的裁定动作。
  *
  * 解决、驳回和升级分别对应结束工单、拒绝诉求和转入更高优先级处理流程。
  */
enum AppealDecisionType:
  case Resolve
  case Reject
  case Escalate

object AppealDecisionType:
  def toString(decision: AppealDecisionType): String =
    decision.toString

  def fromString(value: String): AppealDecisionType =
    AppealDecisionType.valueOf(value)
