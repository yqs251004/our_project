package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealPriority as DomainAppealPriority}
import upickle.default.{ReadWriter, readwriter}

/** AppealPriority 枚举申诉优先级 可使用的公开取值。 */

enum AppealPriority:
  case Low
  case Normal
  case High
  case Critical

  def toDomain: DomainAppealPriority =
    DomainAppealPriority.valueOf(toString)

object AppealPriority:
  given ReadWriter[AppealPriority] = readwriter[String].bimap(_.toString, AppealPriority.valueOf)

  def fromDomain(priority: DomainAppealPriority): AppealPriority =
    AppealPriority.valueOf(priority.toString)
