package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.objects.paifumanagement.MahjongYakuKind
import riichinexus.microservices.tournament.objects.paifumanagement.Yaku

import MahjongYakuCheckSupport.*

private[tournament] object MahjongBasicYakuCheckFunctions:

  val plan: Vector[YakuCheck] =
    Vector(
      checkChiitoitsu,
      checkMenzenTsumo,
      checkDoubleRiichi,
      checkRiichi,
      checkIppatsu,
      checkRinshanKaihou,
      checkHaiteiRaoyue,
      checkHouteiRaoyui,
      checkTanyao,
      checkYakuhaiHaku,
      checkYakuhaiHatsu,
      checkYakuhaiChun,
      checkRoundWind,
      checkSeatWind
    )

  private def checkChiitoitsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.fixedMelds.isEmpty && state.allCounts.count(_ == 2) == 7, MahjongYakuKind.Chiitoitsu, 2)

  private def checkMenzenTsumo(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.target.isEmpty && state.closedHand, MahjongYakuKind.MenzenTsumo, 1)

  private def checkDoubleRiichi(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.doubleRiichi && state.closedHand, MahjongYakuKind.DoubleRiichi, 2)

  private def checkRiichi(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.riichi && !state.context.doubleRiichi && state.closedHand, MahjongYakuKind.Riichi, 1)

  private def checkIppatsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.ippatsu && state.closedHand, MahjongYakuKind.Ippatsu, 1)

  private def checkRinshanKaihou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.rinshan, MahjongYakuKind.RinshanKaihou, 1)

  private def checkHaiteiRaoyue(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.haitei && state.context.target.isEmpty, MahjongYakuKind.HaiteiRaoyue, 1)

  private def checkHouteiRaoyui(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.houtei && state.context.target.nonEmpty, MahjongYakuKind.HouteiRaoyui, 1)

  private def checkTanyao(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.allTileIndices.forall(isSimple) && (state.closedHand || state.context.ruleset.openTanyao),
      MahjongYakuKind.Tanyao,
      1
    )

  private def checkYakuhaiHaku(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).exists(_.tileIndex == Haku)),
      MahjongYakuKind.YakuhaiHaku,
      1
    )

  private def checkYakuhaiHatsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).exists(_.tileIndex == Hatsu)),
      MahjongYakuKind.YakuhaiHatsu,
      1
    )

  private def checkYakuhaiChun(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).exists(_.tileIndex == Chun)),
      MahjongYakuKind.YakuhaiChun,
      1
    )

  private def checkRoundWind(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).exists(_.tileIndex == windToIndex(state.context.roundWind))),
      MahjongYakuKind.RoundWind,
      1
    )

  private def checkSeatWind(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).exists(_.tileIndex == windToIndex(state.seatWind))),
      MahjongYakuKind.SeatWind,
      1
    )

