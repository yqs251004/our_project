package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealPriority as DomainAppealPriority}
import upickle.default.*

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
