package riichinexus.microservices.tournament.objects.paifumanagement

import upickle.default.*

final case class PaifuTile(value: String)

object PaifuTile:
  given ReadWriter[PaifuTile] =
    readwriter[String].bimap(_.value, PaifuTile(_))

  def fromString(value: String): PaifuTile =
    PaifuTile(value)

  def toString(tile: PaifuTile): String =
    tile.value
