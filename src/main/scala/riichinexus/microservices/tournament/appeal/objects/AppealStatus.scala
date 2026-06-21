package riichinexus.microservices.tournament.appeal.objects

/** 申诉工单的生命周期状态。
  *
  * 状态从提交后的开放态进入复核、升级或终态，领域方法会限制只有合适状态的工单才能被解决、驳回或重开。
  */
enum AppealStatus:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated

object AppealStatus:
  def toString(status: AppealStatus): String =
    status.toString

  def fromString(value: String): AppealStatus =
    AppealStatus.valueOf(value)
