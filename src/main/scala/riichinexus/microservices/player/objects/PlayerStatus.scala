package riichinexus.microservices.player.objects

enum PlayerStatus:
  case Active
  case Suspended
  case Banned

object PlayerStatus:
  def toString(status: PlayerStatus): String =
    status match
      case PlayerStatus.Active    => "Active"
      case PlayerStatus.Suspended => "Suspended"
      case PlayerStatus.Banned    => "Banned"

  def fromString(value: String): Either[String, PlayerStatus] =
    value match
      case "Active"    => Right(PlayerStatus.Active)
      case "Suspended" => Right(PlayerStatus.Suspended)
      case "Banned"    => Right(PlayerStatus.Banned)
      case other       => Left(s"Unsupported PlayerStatus value: $other")
