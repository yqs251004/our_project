package riichinexus.microservices.tournament.objects.paifu

/** HandOutcome 枚举手牌Outcome 可使用的公开取值。 */

enum HandOutcome:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

object HandOutcome:
  def toString(outcome: HandOutcome): String =
    outcome.toString

  def fromString(value: String): HandOutcome =
    HandOutcome.valueOf(value)
