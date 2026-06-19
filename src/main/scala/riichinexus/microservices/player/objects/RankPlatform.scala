package riichinexus.microservices.player.objects

/** RankPlatform 枚举等级Platform 可使用的公开取值。 */

enum RankPlatform:
  case Tenhou
  case MahjongSoul
  case Custom

object RankPlatform:
  def toString(platform: RankPlatform): String =
    platform match
      case RankPlatform.Tenhou      => "Tenhou"
      case RankPlatform.MahjongSoul => "MahjongSoul"
      case RankPlatform.Custom      => "Custom"

  def fromString(value: String): Either[String, RankPlatform] =
    value match
      case "Tenhou"      => Right(RankPlatform.Tenhou)
      case "MahjongSoul" => Right(RankPlatform.MahjongSoul)
      case "Custom"      => Right(RankPlatform.Custom)
      case other         => Left(s"Unsupported RankPlatform value: $other")
