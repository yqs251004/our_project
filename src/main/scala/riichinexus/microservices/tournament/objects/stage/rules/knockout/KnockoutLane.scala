package riichinexus.microservices.tournament.objects.stage.rules.knockout

import upickle.default.{ReadWriter, readwriter}

/** KnockoutLane 枚举KnockoutLane 可使用的公开取值。 */

enum KnockoutLane:
  case Championship
  case Bronze
  case Repechage

object KnockoutLane:
  given ReadWriter[KnockoutLane] = readwriter[String].bimap(_.toString, KnockoutLane.valueOf)
