package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.{MahjongHandDecomposition, MahjongHandMeld}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.indexOf
import riichinexus.microservices.tournament.objects.paifu.PaifuTile
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** 执行一组役种检查时共享的牌姿与上下文缓存。 */
private[mahjongcore] final case class MahjongYakuCheckState(
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
