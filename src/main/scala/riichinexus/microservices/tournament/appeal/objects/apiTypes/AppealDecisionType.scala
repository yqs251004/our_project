package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.domain.model.{AppealDecisionType as DomainAppealDecisionType}
import upickle.default.*

enum AppealDecisionType derives CanEqual:
  case Resolve
  case Reject
  case Escalate

  def toDomain: DomainAppealDecisionType =
    DomainAppealDecisionType.valueOf(toString)

object AppealDecisionType:
  given ReadWriter[AppealDecisionType] = readwriter[String].bimap(_.toString, AppealDecisionType.valueOf)

  def fromDomain(decision: DomainAppealDecisionType): AppealDecisionType =
    AppealDecisionType.valueOf(decision.toString)
