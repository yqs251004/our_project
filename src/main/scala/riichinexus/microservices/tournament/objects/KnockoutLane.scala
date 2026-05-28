package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{KnockoutLane as DomainKnockoutLane}
import upickle.default.*

enum KnockoutLane derives CanEqual:
  case Championship
  case Bronze
  case Repechage

  def toDomain: DomainKnockoutLane =
    DomainKnockoutLane.valueOf(toString)

object KnockoutLane:
  given ReadWriter[KnockoutLane] = readwriter[String].bimap(_.toString, KnockoutLane.valueOf)

  def fromDomain(lane: DomainKnockoutLane): KnockoutLane =
    KnockoutLane.valueOf(lane.toString)
