package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.objects.paifumanagement.{MahjongYakuKind, PaifuTile, Yaku}
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

private[tournament] object MahjongYakuCheckSupport:

  final case class MahjongYakuCheckState(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean,
      selectedDecomposition: Option[MahjongHandDecomposition] = None
  ):
    val allTileIndices: Vector[Int] =
      allCounts.indices.filter(allCounts(_) > 0).toVector
    val standardDecompositions: Vector[MahjongHandDecomposition] =
      MahjongHandAnalysisFunctions.standardDecompositions(concealedCounts, fixedMelds)
    val hasOpenMeld: Boolean =
      context.melds.exists(meld => !meld.closed)
    val lastIndex: Int =
      indexOf(context.winningTile)
    val seatWind: SeatWind =
      context.seatByPlayer.getOrElse(context.winner, SeatWind.East)

  type YakuCheck = MahjongYakuCheckState => Vector[Yaku]

  def runPlan(plan: Vector[YakuCheck], state: MahjongYakuCheckState): Vector[Yaku] =
    plan.flatMap(check => check(state)).distinct

  def yakuIf(condition: Boolean, kind: MahjongYakuKind, han: Int): Vector[Yaku] =
    Option.when(condition)(kind.yaku(han)).toVector

  def nonEmptyAllMatch(counts: Array[Int], predicate: Int => Boolean): Boolean =
    counts.indices.exists(index => counts(index) > 0) &&
      counts.indices.forall(index => counts(index) == 0 || predicate(index))

  def tripletLike(decomposition: MahjongHandDecomposition): Vector[MahjongHandMeld] =
    decomposition.melds.filter(_.meldType != MahjongHandMeldType.Shuntsu)

  def isDragonMeld(meld: MahjongHandMeld): Boolean =
    meld.tileIndex >= Haku && meld.tileIndex <= Chun

  def isWindMeld(meld: MahjongHandMeld): Boolean =
    meld.tileIndex >= Ton && meld.tileIndex <= Pei

  def suitCount(indices: Vector[Int]): Int =
    Vector(
      indices.exists(index => index >= Man1 && index <= Man9),
      indices.exists(index => index >= Pin1 && index <= Pin9),
      indices.exists(index => index >= Sou1 && index <= Sou9)
    ).count(identity)

  def everyMeldHasTerminal(decomposition: MahjongHandDecomposition): Boolean =
    decomposition.melds.forall {
      case MahjongHandMeld(MahjongHandMeldType.Shuntsu, start, _) => start % 9 == 0 || start % 9 == 6
      case MahjongHandMeld(_, index, _) => isYaochu(index)
    }

  def isYakuhaiPair(pairIndex: Int, roundWind: SeatWind, seatWind: SeatWind): Boolean =
    (pairIndex >= Haku && pairIndex <= Chun) ||
      pairIndex == windToIndex(roundWind) ||
      pairIndex == windToIndex(seatWind)

  def windToIndex(wind: SeatWind): Int =
    wind match
      case SeatWind.East => Ton
      case SeatWind.South => Nan
      case SeatWind.West => Sha
      case SeatWind.North => Pei

