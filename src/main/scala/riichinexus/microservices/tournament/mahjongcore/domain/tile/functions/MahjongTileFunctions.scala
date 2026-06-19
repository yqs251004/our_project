package riichinexus.microservices.tournament.mahjongcore.domain.tile.functions

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.domain.paifu.functions.PaifuTileFunctions
import riichinexus.microservices.tournament.objects.paifu.{PaifuTile, PaifuTileSuit}

import java.util.Collections
import java.util.Random
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}

/** MahjongTileFunctions 提供麻将牌相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongTileFunctions:

  val TileTypeCount = 34
  val Man1 = 0
  val Man9 = 8
  val Pin1 = 9
  val Pin9 = 17
  val Sou1 = 18
  val Sou9 = 26
  val Ton = 27
  val Nan = 28
  val Sha = 29
  val Pei = 30
  val Haku = 31
  val Hatsu = 32
  val Chun = 33

  private val honorAliases: Map[String, Int] =
    Map(
      "1z" -> Ton,
      "2z" -> Nan,
      "3z" -> Sha,
      "4z" -> Pei,
      "5z" -> Haku,
      "6z" -> Hatsu,
      "7z" -> Chun,
      "east" -> Ton,
      "south" -> Nan,
      "west" -> Sha,
      "north" -> Pei,
      "ton" -> Ton,
      "nan" -> Nan,
      "shaa" -> Sha,
      "pei" -> Pei,
      "haku" -> Haku,
      "hatsu" -> Hatsu,
      "chun" -> Chun,
      "e" -> Ton,
      "s" -> Nan,
      "w" -> Sha,
      "n" -> Pei,
      "p" -> Haku,
      "f" -> Hatsu,
      "c" -> Chun
    )

  def indexOf(tile: PaifuTile): Int =
    val normalizedRank =
      if tile.rank == 0 then 5
      else tile.rank

    tile.suit match
      case PaifuTileSuit.Manzu if normalizedRank >= 1 && normalizedRank <= 9 =>
        normalizedRank - 1
      case PaifuTileSuit.Pinzu if normalizedRank >= 1 && normalizedRank <= 9 =>
        Pin1 + normalizedRank - 1
      case PaifuTileSuit.Souzu if normalizedRank >= 1 && normalizedRank <= 9 =>
        Sou1 + normalizedRank - 1
      case PaifuTileSuit.Honor if normalizedRank >= 1 && normalizedRank <= 7 =>
        Ton + normalizedRank - 1
      case _ =>
        throw IllegalArgumentException(s"Unsupported mahjong tile value: ${PaifuTileFunctions.toString(tile)}")

  def indexOf(value: String): Int =
    val normalized = value.trim.toLowerCase.replace("-", "")
    if honorAliases.contains(normalized) then honorAliases(normalized)
    else if normalized.length >= 2 then
      val rankChar = normalized.head
      val suit = normalized.last
      val rank =
        if rankChar == '0' then 5
        else if rankChar.isDigit then rankChar.asDigit
        else throw IllegalArgumentException(s"Unsupported mahjong tile rank: $value")
      suit match
        case 'm' if rank >= 1 && rank <= 9 => rank - 1
        case 'p' if rank >= 1 && rank <= 9 => Pin1 + rank - 1
        case 's' if rank >= 1 && rank <= 9 => Sou1 + rank - 1
        case 'z' if rank >= 1 && rank <= 7 => Ton + rank - 1
        case _ => throw IllegalArgumentException(s"Unsupported mahjong tile value: $value")
    else throw IllegalArgumentException(s"Unsupported mahjong tile value: $value")

  def tileOf(index: Int, red: Boolean = false): PaifuTile =
    if index >= Man1 && index <= Man9 then
      PaifuTile(
        rank = if red && index == Man1 + 4 then 0 else index + 1,
        suit = PaifuTileSuit.Manzu
      )
    else if index >= Pin1 && index <= Pin9 then
      PaifuTile(
        rank = if red && index == Pin1 + 4 then 0 else index - Pin1 + 1,
        suit = PaifuTileSuit.Pinzu
      )
    else if index >= Sou1 && index <= Sou9 then
      PaifuTile(
        rank = if red && index == Sou1 + 4 then 0 else index - Sou1 + 1,
        suit = PaifuTileSuit.Souzu
      )
    else if index >= Ton && index <= Chun then
      PaifuTile(rank = index - Ton + 1, suit = PaifuTileSuit.Honor)
    else throw IllegalArgumentException(s"Unsupported mahjong tile index: $index")

  def isRed(tile: PaifuTile): Boolean =
    tile.rank == 0 && tile.suit != PaifuTileSuit.Honor

  def normalize(tile: PaifuTile): PaifuTile =
    tileOf(indexOf(tile), red = isRed(tile))

  def sortTiles(tiles: Vector[PaifuTile]): Vector[PaifuTile] =
    tiles.map(normalize).sortBy(tile => (indexOf(tile), if isRed(tile) then 0 else 1))

  def countsOf(tiles: Iterable[PaifuTile]): Array[Int] =
    val counts = Array.fill(TileTypeCount)(0)
    tiles.foreach { tile =>
      counts(indexOf(tile)) += 1
    }
    counts

  def redDoraCount(tiles: Iterable[PaifuTile]): Int =
    tiles.count(isRed)

  def tilesFromCounts(counts: Array[Int]): Vector[PaifuTile] =
    (0 until TileTypeCount).toVector.flatMap { index =>
      Vector.fill(counts(index))(tileOf(index))
    }

  def isSuited(index: Int): Boolean =
    index >= Man1 && index <= Sou9

  def isTerminal(index: Int): Boolean =
    isSuited(index) && (index % 9 == 0 || index % 9 == 8)

  def isHonor(index: Int): Boolean =
    index >= Ton && index <= Chun

  def isYaochu(index: Int): Boolean =
    isTerminal(index) || isHonor(index)

  def isSimple(index: Int): Boolean =
    isSuited(index) && !isYaochu(index)

  def doraFromIndicator(indicatorIndex: Int): Int =
    if indicatorIndex >= Man1 && indicatorIndex <= Sou9 then
      if indicatorIndex % 9 == 8 then indicatorIndex - 8 else indicatorIndex + 1
    else if indicatorIndex >= Ton && indicatorIndex <= Pei then
      if indicatorIndex == Pei then Ton else indicatorIndex + 1
    else if indicatorIndex >= Haku && indicatorIndex <= Chun then
      if indicatorIndex == Chun then Haku else indicatorIndex + 1
    else throw IllegalArgumentException(s"Unsupported dora indicator index: $indicatorIndex")

  def countDora(tiles: Iterable[PaifuTile], indicators: Iterable[PaifuTile]): Int =
    val counts = countsOf(tiles)
    indicators.map(indicator => counts(doraFromIndicator(indexOf(indicator)))).sum

  def allTileTypes: Vector[PaifuTile] =
    (0 until TileTypeCount).toVector.map(tileOf(_))

  def fullWall(ruleset: MahjongRuleset): Vector[PaifuTile] =
    val redFiveIndexes = Vector(Man1 + 4, Pin1 + 4, Sou1 + 4)
      .take(ruleset.normalizedAkaDoraCount)
      .toSet
    (0 until TileTypeCount).toVector.flatMap { index =>
      val red = redFiveIndexes.contains(index)
      if red then tileOf(index, red = true) +: Vector.fill(3)(tileOf(index))
      else Vector.fill(4)(tileOf(index))
    }

  def shuffledWall(seed: String, ruleset: MahjongRuleset): Vector[PaifuTile] =
    val mutable = new java.util.ArrayList[PaifuTile](fullWall(ruleset).asJava)
    Collections.shuffle(mutable, new Random(seed.hashCode.toLong))
    mutable.asScala.toVector

  def hasCopies(tiles: Iterable[PaifuTile], tile: PaifuTile, copies: Int): Boolean =
    countsOf(tiles)(indexOf(tile)) >= copies

  def removeTiles(source: Vector[PaifuTile], tilesToRemove: Vector[PaifuTile]): Option[Vector[PaifuTile]] =
    removeTilesWithRemoved(source, tilesToRemove).map(_._1)

  def removeTilesWithRemoved(
      source: Vector[PaifuTile],
      tilesToRemove: Vector[PaifuTile]
  ): Option[(Vector[PaifuTile], Vector[PaifuTile])] =
    val remaining = source.zipWithIndex.toBuffer
    val removed = scala.collection.mutable.ArrayBuffer.empty[PaifuTile]
    var valid = true
    tilesToRemove.foreach { tile =>
      if valid then
        val normalizedTile = normalize(tile)
        val exactPosition = remaining.indexWhere { case (candidate, _) => normalize(candidate) == normalizedTile }
        val position =
          if exactPosition >= 0 then exactPosition
          else
            val targetIndex = indexOf(tile)
            remaining.indexWhere { case (candidate, _) => indexOf(candidate) == targetIndex }
        if position < 0 then valid = false
        else removed += remaining.remove(position)._1
    }
    if valid then Some(remaining.map(_._1).toVector -> removed.toVector) else None
