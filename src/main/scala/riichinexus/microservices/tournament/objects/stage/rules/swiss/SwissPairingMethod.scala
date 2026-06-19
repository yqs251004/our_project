package riichinexus.microservices.tournament.objects.stage.rules.swiss

import upickle.default.{ReadWriter, readwriter}

/** SwissPairingMethod 枚举SwissPairingMethod 可使用的公开取值。 */

enum SwissPairingMethod:
  case BalancedElo
  case Snake

  def value: String =
    this match
      case BalancedElo => "balanced-elo"
      case Snake       => "snake"

object SwissPairingMethod:
  given ReadWriter[SwissPairingMethod] =
    readwriter[String].bimap(_.value, fromValue)

  def fromValue(value: String): SwissPairingMethod =
    value match
      case "balanced-elo" => BalancedElo
      case "snake"        => Snake
      case other          => throw IllegalArgumentException(s"Unknown swiss pairing method $other")
