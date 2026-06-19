package riichinexus.microservices.tournament.objects.paifumanagement

import upickle.default.{ReadWriter, readwriter}

/** PaifuTile 表示前后端共享的牌谱牌 数据结构。 */

final case class PaifuTile(value: String)

object PaifuTile:
  given ReadWriter[PaifuTile] =
    readwriter[String].bimap(_.value, PaifuTile(_))

  def fromString(value: String): PaifuTile =
    PaifuTile(value)

  def toString(tile: PaifuTile): String =
    tile.value
