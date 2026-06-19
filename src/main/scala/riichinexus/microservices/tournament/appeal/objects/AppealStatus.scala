package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealStatus as DomainAppealStatus}
import upickle.default.{ReadWriter, readwriter}

/** AppealStatus 枚举申诉状态 可使用的公开取值。 */

enum AppealStatus:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated

  def toDomain: DomainAppealStatus =
    DomainAppealStatus.valueOf(toString)

object AppealStatus:
  given ReadWriter[AppealStatus] = readwriter[String].bimap(_.toString, AppealStatus.valueOf)

  def fromDomain(status: DomainAppealStatus): AppealStatus =
    AppealStatus.valueOf(status.toString)
