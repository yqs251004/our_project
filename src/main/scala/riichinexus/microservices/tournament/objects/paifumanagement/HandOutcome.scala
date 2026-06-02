package riichinexus.microservices.tournament.objects.paifumanagement

import upickle.default.*

enum HandOutcome:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

object HandOutcome:
  given ReadWriter[HandOutcome] =
    readwriter[String].bimap(_.toString, HandOutcome.valueOf)
