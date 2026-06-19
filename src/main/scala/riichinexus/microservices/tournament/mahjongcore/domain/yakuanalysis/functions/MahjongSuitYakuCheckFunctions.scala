package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.MahjongHandMeldType
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{isHonor, isYaochu}
import riichinexus.microservices.tournament.objects.paifu.MahjongYakuKind
import riichinexus.microservices.tournament.objects.paifu.Yaku

import MahjongYakuCheckSupport.{MahjongYakuCheckState, YakuCheck, everyMeldHasTerminal, suitCount, yakuIf}

/** MahjongSuitYakuCheckFunctions 提供麻将花色役种检查函数 相关的领域校验和权限判断。 */

private[functions] object MahjongSuitYakuCheckFunctions:

  val plan: Vector[YakuCheck] =
    Vector(
      checkChinitsu,
      checkHonitsu,
      checkHonroutou,
      checkJunchan,
      checkChanta
    )

  private def checkChinitsu(state: MahjongYakuCheckState): Vector[Yaku] =
    val hasHonor = state.allTileIndices.exists(isHonor)
    yakuIf(suitCount(state.allTileIndices) == 1 && !hasHonor, MahjongYakuKind.Chinitsu, if state.closedHand then 6 else 5)

  private def checkHonitsu(state: MahjongYakuCheckState): Vector[Yaku] =
    val hasHonor = state.allTileIndices.exists(isHonor)
    yakuIf(suitCount(state.allTileIndices) == 1 && hasHonor, MahjongYakuKind.Honitsu, if state.closedHand then 3 else 2)

  private def checkHonroutou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.allTileIndices.forall(isYaochu), MahjongYakuKind.Honroutou, 2)

  private def checkJunchan(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      !state.allTileIndices.exists(isHonor) &&
        state.selectedDecomposition.exists(decomposition =>
          everyMeldHasTerminal(decomposition) &&
            isYaochu(decomposition.pairIndex) &&
            decomposition.melds.exists(_.meldType == MahjongHandMeldType.Shuntsu)
        ),
      MahjongYakuKind.Junchan,
      if state.closedHand then 3 else 2
    )

  private def checkChanta(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.allTileIndices.exists(isHonor) &&
        state.selectedDecomposition.exists(decomposition => everyMeldHasTerminal(decomposition) && isYaochu(decomposition.pairIndex)),
      MahjongYakuKind.Chanta,
      if state.closedHand then 2 else 1
    )

