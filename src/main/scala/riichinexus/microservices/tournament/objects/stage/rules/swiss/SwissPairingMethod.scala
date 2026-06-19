package riichinexus.microservices.tournament.objects.stage.rules.swiss

/** SwissPairingMethod 枚举SwissPairingMethod 可使用的公开取值。 */

enum SwissPairingMethod:
  case BalancedElo
  case Snake

object SwissPairingMethod:
  def toString(method: SwissPairingMethod): String =
    method match
      case BalancedElo => "balanced-elo"
      case Snake       => "snake"

  def fromString(value: String): SwissPairingMethod =
    value match
      case "balanced-elo" => BalancedElo
      case "snake"        => Snake
      case other          => throw IllegalArgumentException(s"Unknown swiss pairing method $other")
