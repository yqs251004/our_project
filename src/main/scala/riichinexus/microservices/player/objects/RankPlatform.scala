package riichinexus.microservices.player.objects

/** 玩家段位来源平台。
  *
  * 段位快照用它区分天凤、雀魂和自定义来源，方便之后按平台解析段位文本或标准化分数。
  */
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
