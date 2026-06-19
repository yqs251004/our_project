package riichinexus.microservices.tournament.appeal.objects

/** AppealStatus 枚举申诉状态 可使用的公开取值。 */

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
