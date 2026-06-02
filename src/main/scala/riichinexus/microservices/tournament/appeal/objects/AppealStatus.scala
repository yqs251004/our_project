package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealStatus as DomainAppealStatus}
import upickle.default.*

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
