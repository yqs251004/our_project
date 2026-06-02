package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import upickle.default.*

enum KnockoutLane:
  case Championship
  case Bronze
  case Repechage

object KnockoutLane:
  given ReadWriter[KnockoutLane] = readwriter[String].bimap(_.toString, KnockoutLane.valueOf)
