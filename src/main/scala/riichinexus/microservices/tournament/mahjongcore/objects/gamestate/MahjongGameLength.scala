package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

/** 实时麻将桌采用的牌局长度。
  *
  * OneKyoku 用于单局演示或测试，Tonpu 和 Hanchan 分别对应东风战与半庄，规则集和终局判断会读取该值。
  */
enum MahjongGameLength:
  case OneKyoku
  case Tonpu
  case Hanchan

object MahjongGameLength:
  def toString(length: MahjongGameLength): String =
    length.toString

  def fromString(value: String): MahjongGameLength =
    MahjongGameLength.valueOf(value)
