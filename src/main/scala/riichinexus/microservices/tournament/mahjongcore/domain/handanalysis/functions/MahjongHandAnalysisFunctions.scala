package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile

private[tournament] object MahjongHandAnalysisFunctions:

  private val YaochuIndices: Vector[Int] =
    Vector(Man1, Man9, Pin1, Pin9, Sou1, Sou9, Ton, Nan, Sha, Pei, Haku, Hatsu, Chun)

  def countsOf(tiles: Iterable[PaifuTile]): Array[Int] =
    MahjongTileFunctions.countsOf(tiles)

  def calculateShanten(
      tiles: Iterable[PaifuTile],
      completedMelds: Int = 0,
      allowSpecialHands: Boolean = true
  ): Int =
    calculateShanten(countsOf(tiles), completedMelds, allowSpecialHands)

  def calculateShanten(
      counts: Array[Int],
      completedMelds: Int,
      allowSpecialHands: Boolean
  ): Int =
    val standard = calculateStandardShanten(counts, completedMelds)
    if completedMelds > 0 || !allowSpecialHands then standard
    else standard.min(calculateChiitoitsuShanten(counts)).min(calculateKokushiShanten(counts))

  def isWinning(
      tiles: Iterable[PaifuTile],
      completedMelds: Int = 0,
      allowSpecialHands: Boolean = true
  ): Boolean =
    isWinning(countsOf(tiles), completedMelds, allowSpecialHands)

  def isWinning(
      counts: Array[Int],
      completedMelds: Int,
      allowSpecialHands: Boolean
  ): Boolean =
    calculateShanten(counts, completedMelds, allowSpecialHands) == -1

  def waitingTiles(
      tiles: Iterable[PaifuTile],
      completedMelds: Int = 0,
      allowSpecialHands: Boolean = true
  ): Vector[PaifuTile] =
    val counts = countsOf(tiles)
    (0 until TileTypeCount).toVector.flatMap { index =>
      if counts(index) >= 4 then Vector.empty
      else
        val candidate = counts.clone()
        candidate(index) += 1
        if isWinning(candidate, completedMelds, allowSpecialHands) then Vector(tileOf(index))
        else Vector.empty
    }

  def helpfulTiles(
      tiles: Iterable[PaifuTile],
      visibleTiles: Iterable[PaifuTile] = Vector.empty,
      completedMelds: Int = 0,
      allowSpecialHands: Boolean = true
  ): Map[PaifuTile, Int] =
    val counts = countsOf(tiles)
    val visibleCounts = countsOf(visibleTiles)
    val currentShanten = calculateShanten(counts, completedMelds, allowSpecialHands)
    if currentShanten < 0 then Map.empty
    else
      (0 until TileTypeCount).flatMap { drawIndex =>
        if visibleCounts(drawIndex) + counts(drawIndex) >= 4 then None
        else
          val withDraw = counts.clone()
          withDraw(drawIndex) += 1
          val bestAfterDiscard =
            (0 until TileTypeCount).foldLeft(8) { (best, discardIndex) =>
              if withDraw(discardIndex) <= 0 then best
              else
                val afterDiscard = withDraw.clone()
                afterDiscard(discardIndex) -= 1
                best.min(calculateShanten(afterDiscard, completedMelds, allowSpecialHands))
            }
          Option.when(bestAfterDiscard < currentShanten)(
            tileOf(drawIndex) -> (4 - visibleCounts(drawIndex) - counts(drawIndex))
          )
      }.toMap

  def standardDecomposition(
      tiles: Iterable[PaifuTile],
      fixedMelds: Vector[MahjongHandMeld] = Vector.empty
  ): Option[MahjongHandDecomposition] =
    standardDecompositions(countsOf(tiles), fixedMelds).headOption

  def standardDecomposition(
      counts: Array[Int],
      fixedMelds: Vector[MahjongHandMeld]
  ): Option[MahjongHandDecomposition] =
    standardDecompositions(counts, fixedMelds).headOption

  def standardDecompositions(
      counts: Array[Int],
      fixedMelds: Vector[MahjongHandMeld]
  ): Vector[MahjongHandDecomposition] =
    val neededMelds = 4 - fixedMelds.size
    if neededMelds < 0 then Vector.empty
    else
      (0 until TileTypeCount).toVector.flatMap { pairIndex =>
        if counts(pairIndex) < 2 then Vector.empty
        else
          val temp = counts.clone()
          temp(pairIndex) -= 2
          decomposeMelds(temp, neededMelds, Vector.empty).map { concealedMelds =>
            MahjongHandDecomposition(fixedMelds ++ concealedMelds, pairIndex)
          }
      }

  private def calculateStandardShanten(counts: Array[Int], completedMelds: Int): Int =
    var minShanten = 8

    def search(index: Int, melds: Int, taatsu: Int, pairs: Int, current: Array[Int]): Unit =
      var cursor = index
      while cursor < TileTypeCount && current(cursor) == 0 do cursor += 1

      if cursor == TileTypeCount then
        val groups = pairs + taatsu
        var shanten = 8 - melds * 2 - math.min(groups, 4 - melds)
        if pairs >= 1 && groups + melds >= 5 then shanten -= 1
        minShanten = math.min(minShanten, shanten)
      else
        if current(cursor) >= 3 then
          current(cursor) -= 3
          search(cursor, melds + 1, taatsu, pairs, current)
          current(cursor) += 3

        if isSuited(cursor) && cursor % 9 <= 6 && current(cursor + 1) > 0 && current(cursor + 2) > 0 then
          current(cursor) -= 1
          current(cursor + 1) -= 1
          current(cursor + 2) -= 1
          search(cursor, melds + 1, taatsu, pairs, current)
          current(cursor) += 1
          current(cursor + 1) += 1
          current(cursor + 2) += 1

        if current(cursor) >= 2 then
          current(cursor) -= 2
          search(cursor, melds, taatsu, pairs + 1, current)
          current(cursor) += 2

        if isSuited(cursor) && cursor % 9 <= 7 && current(cursor + 1) > 0 then
          current(cursor) -= 1
          current(cursor + 1) -= 1
          search(cursor, melds, taatsu + 1, pairs, current)
          current(cursor) += 1
          current(cursor + 1) += 1

        if isSuited(cursor) && cursor % 9 <= 6 && current(cursor + 2) > 0 then
          current(cursor) -= 1
          current(cursor + 2) -= 1
          search(cursor, melds, taatsu + 1, pairs, current)
          current(cursor) += 1
          current(cursor + 2) += 1

        search(cursor + 1, melds, taatsu, pairs, current)

    search(0, completedMelds, 0, 0, counts.clone())
    minShanten

  private def calculateChiitoitsuShanten(counts: Array[Int]): Int =
    val pairCount = counts.count(_ >= 2)
    val distinctCount = counts.count(_ > 0)
    6 - pairCount + math.max(0, 7 - distinctCount)

  private def calculateKokushiShanten(counts: Array[Int]): Int =
    val yaochuTypes = YaochuIndices.count(index => counts(index) > 0)
    val hasPair = YaochuIndices.exists(index => counts(index) >= 2)
    13 - yaochuTypes - (if hasPair then 1 else 0)

  private def decomposeMelds(
      counts: Array[Int],
      neededMelds: Int,
      acc: Vector[MahjongHandMeld]
  ): Vector[Vector[MahjongHandMeld]] =
    if acc.size == neededMelds then
      if counts.forall(_ == 0) then Vector(acc) else Vector.empty
    else
      val first = counts.indexWhere(_ > 0)
      if first < 0 then Vector(acc)
      else
        val triplet =
          if counts(first) >= 3 then
            counts(first) -= 3
            val result = decomposeMelds(
              counts,
              neededMelds,
              acc :+ MahjongHandMeld(MahjongHandMeldType.Koutsu, first, concealed = true)
            )
            counts(first) += 3
            result
          else Vector.empty

        val sequence =
          if isSuited(first) && first % 9 <= 6 && counts(first + 1) > 0 && counts(first + 2) > 0 then
              counts(first) -= 1
              counts(first + 1) -= 1
              counts(first + 2) -= 1
              val result = decomposeMelds(
                counts,
                neededMelds,
                acc :+ MahjongHandMeld(MahjongHandMeldType.Shuntsu, first, concealed = true)
              )
              counts(first) += 1
              counts(first + 1) += 1
              counts(first + 2) += 1
              result
          else Vector.empty

        triplet ++ sequence
