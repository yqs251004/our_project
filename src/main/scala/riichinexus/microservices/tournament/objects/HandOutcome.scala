package riichinexus.microservices.tournament.objects

import upickle.default.*

enum HandOutcome derives CanEqual:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

object HandOutcome:
  given ReadWriter[HandOutcome] =
    readwriter[String].bimap(_.toString, HandOutcome.valueOf)
