package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile

/** PaifuTileFunctions 提供牌谱牌相关的领域计算、校验和转换函数。 */

private[tournament] object PaifuTileFunctions:
  private val TilePattern = "^[0-9][mps]$|^[1-7]z$".r

  def isValid(value: String): Boolean =
    TilePattern.matches(value)

  def validate(tile: PaifuTile): PaifuTile =
    require(isValid(tile.value), s"Invalid paifu tile: ${tile.value}")
    tile

  def validateAll(tiles: Iterable[PaifuTile], context: String): Unit =
    tiles.foreach { tile =>
      require(isValid(tile.value), s"$context contains invalid paifu tile: ${tile.value}")
    }

  def toTileIndex(tile: PaifuTile): Option[Int] =
    if !isValid(tile.value) then None
    else
      val numberChar = tile.value.charAt(0)
      val suitChar = tile.value.charAt(1)
      val normalizedNumber =
        if numberChar == '0' then 5
        else numberChar.asDigit

      suitChar match
        case 'm' => Some(normalizedNumber - 1)
        case 'p' => Some(9 + normalizedNumber - 1)
        case 's' => Some(18 + normalizedNumber - 1)
        case 'z' => Some(27 + normalizedNumber - 1)
        case _   => None
