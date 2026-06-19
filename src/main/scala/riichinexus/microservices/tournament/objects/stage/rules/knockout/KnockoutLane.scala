package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** KnockoutLane 枚举KnockoutLane 可使用的公开取值。 */

enum KnockoutLane:
  case Championship
  case Bronze
  case Repechage

object KnockoutLane:
  def toString(lane: KnockoutLane): String =
    lane.toString

  def fromString(value: String): KnockoutLane =
    KnockoutLane.valueOf(value)
