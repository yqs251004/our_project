package riichinexus.microservices.tournament.objects.paifu

import upickle.default.{ReadWriter, macroRW}

final case class PaifuTile(rank: Int, suit: PaifuTileSuit)

object PaifuTile:
  given ReadWriter[PaifuTile] = macroRW
