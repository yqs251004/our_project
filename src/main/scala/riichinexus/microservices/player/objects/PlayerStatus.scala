package riichinexus.microservices.player.objects

/** 玩家账号在业务系统中的可用状态。
  *
  * 状态会影响登录后的可操作范围、公共榜单展示和平台管理动作；封禁玩家还会保留封禁原因。
  */
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
