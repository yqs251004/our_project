package riichinexus.microservices.tournament.domain.paifumanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile

object PaifuTileFunctions:
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
