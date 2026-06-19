package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import upickle.default.{ReadWriter, readwriter}

/** MahjongGameLength 表示前后端共享的麻将牌局长度。 */
enum MahjongGameLength:
  case OneKyoku
  case Tonpu
  case Hanchan

object MahjongGameLength:
  given ReadWriter[MahjongGameLength] =
    readwriter[String].bimap(_.toString, MahjongGameLength.valueOf)
