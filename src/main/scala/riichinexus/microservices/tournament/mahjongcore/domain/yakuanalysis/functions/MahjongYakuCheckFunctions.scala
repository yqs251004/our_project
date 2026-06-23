package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.{MahjongHandDecomposition, MahjongHandMeld}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.{MahjongWinContext, MahjongYakuCandidate, MahjongYakuCheckState}
import riichinexus.microservices.tournament.objects.paifu.{PaifuTile, Yaku}

import MahjongYakuCheckSupport.runPlan

/** MahjongYakuCheckFunctions 提供麻将役种检查函数 相关的领域校验和权限判断。 */

private[mahjongcore] object MahjongYakuCheckFunctions:

  private val ordinaryPlan: Vector[MahjongYakuCheckState => Vector[Yaku]] =
    MahjongBasicYakuCheckFunctions.plan ++
      MahjongShapeYakuCheckFunctions.plan ++
      MahjongSuitYakuCheckFunctions.plan

  def yakumanYaku(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Vector[Yaku] =
    runPlan(MahjongYakumanCheckFunctions.plan, checkState(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand))

  def ordinaryYaku(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Vector[Yaku] =
    ordinaryYakuCandidates(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
      .maxByOption(candidate => candidate.yaku.map(_.han).sum)
      .map(_.yaku)
      .getOrElse(Vector.empty)

  def ordinaryYakuCandidates(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Vector[MahjongYakuCandidate] =
    val baseState = checkState(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
    val standardCandidates =
      baseState.standardDecompositions.map { decomposition =>
        val selectedState = baseState.copy(selectedDecomposition = Some(decomposition))
        MahjongYakuCandidate(runPlan(ordinaryPlan, selectedState), Some(decomposition))
      }
    val chiitoitsuCandidate =
      if fixedMelds.isEmpty && allCounts.count(_ == 2) == 7 then
        val selectedState = baseState.copy(selectedDecomposition = None)
        Vector(MahjongYakuCandidate(runPlan(ordinaryPlan, selectedState), None))
      else Vector.empty

    (standardCandidates ++ chiitoitsuCandidate).filter(_.yaku.nonEmpty)

  def addDora(
      yaku: Vector[Yaku],
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Vector[Yaku] =
    yaku ++ runPlan(MahjongDoraCheckFunctions.plan, checkState(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand))

  private def checkState(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): MahjongYakuCheckState =
    MahjongYakuCheckState(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
