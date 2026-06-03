package riichinexus.microservices.tournament.mahjongcore.domain.tile.functions

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile

import java.util.Collections
import java.util.Random
import scala.jdk.CollectionConverters.*

object MahjongTileFunctions:

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
    indexOf(tile.value)

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
      PaifuTile(if red && index == Man1 + 4 then "0m" else s"${index + 1}m")
    else if index >= Pin1 && index <= Pin9 then
      PaifuTile(if red && index == Pin1 + 4 then "0p" else s"${index - Pin1 + 1}p")
    else if index >= Sou1 && index <= Sou9 then
      PaifuTile(if red && index == Sou1 + 4 then "0s" else s"${index - Sou1 + 1}s")
    else if index >= Ton && index <= Chun then PaifuTile(s"${index - Ton + 1}z")
    else throw IllegalArgumentException(s"Unsupported mahjong tile index: $index")

  def isRed(tile: PaifuTile): Boolean =
    val normalized = tile.value.trim.toLowerCase
    normalized == "0m" || normalized == "0p" || normalized == "0s" ||
      normalized.endsWith("dora")

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
    (0 until TileTypeCount).toVector.flatMap { index =>
      val red =
        ruleset.akaDora &&
          (index == Man1 + 4 || index == Pin1 + 4 || index == Sou1 + 4)
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
    val remaining = source.zipWithIndex.toBuffer
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
        else remaining.remove(position)
    }
    if valid then Some(remaining.map(_._1).toVector) else None
