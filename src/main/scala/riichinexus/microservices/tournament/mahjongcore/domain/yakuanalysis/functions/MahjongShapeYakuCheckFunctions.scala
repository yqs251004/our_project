package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.MahjongHandMeldType
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{Chun, Haku, Man1, Pin1, Sou1}
import riichinexus.microservices.tournament.objects.paifu.MahjongYakuKind
import riichinexus.microservices.tournament.objects.paifu.Yaku

import MahjongYakuCheckSupport.{MahjongYakuCheckState, YakuCheck, isDragonMeld, isYakuhaiPair, tripletLike, yakuIf}

/** MahjongShapeYakuCheckFunctions 提供麻将牌型役种检查函数 相关的领域校验和权限判断。 */

private[functions] object MahjongShapeYakuCheckFunctions:

  val plan: Vector[YakuCheck] =
    Vector(
      checkPinfu,
      checkRyanpeikou,
      checkIipeikou,
      checkToitoi,
      checkSanankou,
      checkSankantsu,
      checkShousangen,
      checkSanshokuDoujun,
      checkIttsu,
      checkSanshokuDoukou
    )

  private def checkPinfu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.closedHand &&
        state.selectedDecomposition.exists(decomposition =>
          decomposition.melds.count(_.meldType == MahjongHandMeldType.Shuntsu) == 4 &&
            !isYakuhaiPair(decomposition.pairIndex, state.context.roundWind, state.seatWind)
        ),
      MahjongYakuKind.Pinfu,
      1
    )

  private def checkRyanpeikou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.closedHand && maxRepeatedSequencePairCount(state) >= 2, MahjongYakuKind.Ryanpeikou, 3)

  private def checkIipeikou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.closedHand && maxRepeatedSequencePairCount(state) == 1, MahjongYakuKind.Iipeikou, 1)

  private def checkToitoi(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => decomposition.melds.forall(_.meldType != MahjongHandMeldType.Shuntsu)),
      MahjongYakuKind.Toitoi,
      2
    )

  private def checkSanankou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition => tripletLike(decomposition).count(_.concealed) == 3),
      MahjongYakuKind.Sanankou,
      2
    )

  private def checkSankantsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(_.melds.count(_.meldType == MahjongHandMeldType.Kantsu) == 3),
      MahjongYakuKind.Sankantsu,
      2
    )

  private def checkShousangen(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.selectedDecomposition.exists(decomposition =>
        tripletLike(decomposition).count(isDragonMeld) == 2 &&
          decomposition.pairIndex >= Haku &&
          decomposition.pairIndex <= Chun
      ),
      MahjongYakuKind.Shousangen,
      2
    )

  private def checkSanshokuDoujun(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(hasThreeSuitSequences(state), MahjongYakuKind.SanshokuDoujun, if state.closedHand then 2 else 1)

  private def checkIttsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(hasStraightSequences(state), MahjongYakuKind.Ittsu, if state.closedHand then 2 else 1)

  private def checkSanshokuDoukou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(hasThreeSuitTriplets(state), MahjongYakuKind.SanshokuDoukou, 2)

  private def maxRepeatedSequencePairCount(state: MahjongYakuCheckState): Int =
    state.selectedDecomposition.toVector.map { decomposition =>
      decomposition.melds
        .filter(_.meldType == MahjongHandMeldType.Shuntsu)
        .map(_.tileIndex)
        .groupBy(identity)
        .values
        .count(_.size >= 2)
    }.maxOption.getOrElse(0)

  private def hasThreeSuitSequences(state: MahjongYakuCheckState): Boolean =
    state.selectedDecomposition.exists { decomposition =>
      val starts = decomposition.melds.filter(_.meldType == MahjongHandMeldType.Shuntsu).map(_.tileIndex).toSet
      (0 to 6).exists(start => starts.contains(Man1 + start) && starts.contains(Pin1 + start) && starts.contains(Sou1 + start))
    }

  private def hasStraightSequences(state: MahjongYakuCheckState): Boolean =
    state.selectedDecomposition.exists { decomposition =>
      val starts = decomposition.melds.filter(_.meldType == MahjongHandMeldType.Shuntsu).map(_.tileIndex).toSet
      Vector(Man1, Pin1, Sou1).exists(suitStart => starts.contains(suitStart) && starts.contains(suitStart + 3) && starts.contains(suitStart + 6))
    }

  private def hasThreeSuitTriplets(state: MahjongYakuCheckState): Boolean =
    state.selectedDecomposition.exists { decomposition =>
      val tripletStarts = tripletLike(decomposition).map(_.tileIndex).toSet
      (0 to 8).exists(rank => tripletStarts.contains(Man1 + rank) && tripletStarts.contains(Pin1 + rank) && tripletStarts.contains(Sou1 + rank))
    }

