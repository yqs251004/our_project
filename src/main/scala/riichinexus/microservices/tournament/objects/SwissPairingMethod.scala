package riichinexus.microservices.tournament.objects

import upickle.default.*

enum SwissPairingMethod derives CanEqual:
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
