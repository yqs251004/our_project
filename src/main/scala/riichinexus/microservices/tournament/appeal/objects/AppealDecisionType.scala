package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealDecisionType as DomainAppealDecisionType}
import upickle.default.{ReadWriter, readwriter}

/** AppealDecisionType 枚举申诉裁定类型 可使用的公开取值。 */

enum AppealDecisionType:
  case Resolve
  case Reject
  case Escalate

  def toDomain: DomainAppealDecisionType =
    DomainAppealDecisionType.valueOf(toString)

object AppealDecisionType:
  given ReadWriter[AppealDecisionType] = readwriter[String].bimap(_.toString, AppealDecisionType.valueOf)

  def fromDomain(decision: DomainAppealDecisionType): AppealDecisionType =
    AppealDecisionType.valueOf(decision.toString)
