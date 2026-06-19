package riichinexus.microservices.tournament.objects.paifumanagement

import upickle.default.{ReadWriter, readwriter}

/** HandOutcome 枚举手牌Outcome 可使用的公开取值。 */

enum HandOutcome:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

object HandOutcome:
  given ReadWriter[HandOutcome] =
    readwriter[String].bimap(_.toString, HandOutcome.valueOf)
