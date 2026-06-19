package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifu.{PaifuTile, PaifuTileSuit}

/** PaifuTileFunctions 提供牌谱牌相关的领域计算、校验和转换函数。 */

private[tournament] object PaifuTileFunctions:
  private val TilePattern = "^[0-9][mps]$|^[1-7]z$".r

  def isValid(value: String): Boolean =
    TilePattern.matches(value)

  def isValid(rank: Int, suit: PaifuTileSuit): Boolean =
    suit match
      case PaifuTileSuit.Manzu | PaifuTileSuit.Pinzu | PaifuTileSuit.Souzu =>
        rank >= 0 && rank <= 9
      case PaifuTileSuit.Honor =>
        rank >= 1 && rank <= 7

  def isValid(tile: PaifuTile): Boolean =
    isValid(tile.rank, tile.suit)

  def fromString(value: String): PaifuTile =
    val normalized = value.trim.toLowerCase
    require(isValid(normalized), s"Invalid paifu tile: $value")
    PaifuTile(
      rank = normalized.head.asDigit,
      suit = PaifuTileSuit.fromString(normalized.last.toString)
    )

  def toString(tile: PaifuTile): String =
    s"${tile.rank}${PaifuTileSuit.toString(tile.suit)}"

  def validate(tile: PaifuTile): PaifuTile =
    require(isValid(tile), s"Invalid paifu tile: ${toString(tile)}")
    tile

  def validateAll(tiles: Iterable[PaifuTile], context: String): Unit =
    tiles.foreach { tile =>
      require(isValid(tile), s"$context contains invalid paifu tile: ${toString(tile)}")
    }

  def toTileIndex(tile: PaifuTile): Option[Int] =
    if !isValid(tile) then None
    else
      val normalizedNumber =
        if tile.rank == 0 then 5
        else tile.rank

      tile.suit match
        case PaifuTileSuit.Manzu => Some(normalizedNumber - 1)
        case PaifuTileSuit.Pinzu => Some(9 + normalizedNumber - 1)
        case PaifuTileSuit.Souzu => Some(18 + normalizedNumber - 1)
        case PaifuTileSuit.Honor => Some(27 + normalizedNumber - 1)
