package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

/** MahjongGameLength 表示前后端共享的麻将牌局长度。 */
enum MahjongGameLength:
  case OneKyoku
  case Tonpu
  case Hanchan

object MahjongGameLength:
  def toString(length: MahjongGameLength): String =
    length.toString

  def fromString(value: String): MahjongGameLength =
    MahjongGameLength.valueOf(value)
