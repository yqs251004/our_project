package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import upickle.default.*

/** 描述副露或杠子的种类，供实时桌面和内部状态共同标记牌组来源。 */
enum MahjongMeldType:
  case Chi
  case Pon
  case OpenKan
  case ClosedKan
  case AddedKan

object MahjongMeldType:
  given ReadWriter[MahjongMeldType] =
    readwriter[String].bimap(_.toString, MahjongMeldType.valueOf)
