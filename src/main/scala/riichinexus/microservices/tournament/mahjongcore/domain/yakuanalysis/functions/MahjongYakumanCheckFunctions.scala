package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.MahjongHandMeldType
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{Chun, Haku, Hatsu, Man1, Man9, Nan, Pei, Pin1, Pin9, Sha, Sou1, Sou9, Ton, isHonor, isTerminal}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongYakuCheckState
import riichinexus.microservices.tournament.objects.paifu.MahjongYakuKind
import riichinexus.microservices.tournament.objects.paifu.Yaku

import MahjongYakuCheckSupport.{isDragonMeld, isWindMeld, nonEmptyAllMatch, tripletLike, yakuIf}

/** MahjongYakumanCheckFunctions 提供麻将役满检查函数 相关的领域校验和权限判断。 */

private[mahjongcore] object MahjongYakumanCheckFunctions:

  val plan: Vector[MahjongYakuCheckState => Vector[Yaku]] =
    Vector(
      checkKokushiMusou,
      checkChuurenPoutou,
      checkTsuuiisou,
      checkRyuuiisou,
      checkChinroutou,
      checkSuuankou,
      checkDaisangen,
      checkDaisuushi,
      checkShousuushi,
      checkSuukantsu,
      checkTenhou,
      checkChiihou
    )

  private def checkKokushiMusou(state: MahjongYakuCheckState): Vector[Yaku] =
    if state.fixedMelds.isEmpty && isKokushi(state.allCounts) then
      if state.allCounts(state.lastIndex) == 2 then Vector(MahjongYakuKind.KokushiMusouThirteenWait.yaku(26))
      else Vector(MahjongYakuKind.KokushiMusou.yaku(13))
    else Vector.empty

  private def checkChuurenPoutou(state: MahjongYakuCheckState): Vector[Yaku] =
    if state.fixedMelds.isEmpty && isChuuren(state.allCounts) then
      if isPureChuurenWait(state.allCounts, state.lastIndex) then Vector(MahjongYakuKind.PureChuurenPoutou.yaku(26))
      else Vector(MahjongYakuKind.ChuurenPoutou.yaku(13))
    else Vector.empty

  private def checkTsuuiisou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(nonEmptyAllMatch(state.allCounts, isHonor), MahjongYakuKind.Tsuuiisou, 13)

  private def checkRyuuiisou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(nonEmptyAllMatch(state.allCounts, isGreen), MahjongYakuKind.Ryuuiisou, 13)

  private def checkChinroutou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(nonEmptyAllMatch(state.allCounts, isTerminal), MahjongYakuKind.Chinroutou, 13)

  private def checkSuuankou(state: MahjongYakuCheckState): Vector[Yaku] =
    state.standardDecompositions.headOption.toVector.flatMap { decomposition =>
      val concealedTriplets = tripletLike(decomposition).count(_.concealed)
      if !state.hasOpenMeld && concealedTriplets == 4 then
        if decomposition.pairIndex == state.lastIndex then Vector(MahjongYakuKind.SuuankouTanki.yaku(26))
        else Vector(MahjongYakuKind.Suuankou.yaku(13))
      else Vector.empty
    }

  private def checkDaisangen(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.standardDecompositions.exists(decomposition => tripletLike(decomposition).count(isDragonMeld) == 3),
      MahjongYakuKind.Daisangen,
      13
    )

  private def checkDaisuushi(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.standardDecompositions.exists(decomposition => tripletLike(decomposition).count(isWindMeld) == 4),
      MahjongYakuKind.Daisuushi,
      26
    )

  private def checkShousuushi(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.standardDecompositions.exists(decomposition =>
        tripletLike(decomposition).count(isWindMeld) == 3 &&
          decomposition.pairIndex >= Ton &&
          decomposition.pairIndex <= Pei
      ),
      MahjongYakuKind.Shousuushi,
      13
    )

  private def checkSuukantsu(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(
      state.standardDecompositions.exists(_.melds.count(_.meldType == MahjongHandMeldType.Kantsu) == 4),
      MahjongYakuKind.Suukantsu,
      13
    )

  private def checkTenhou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.tenhou, MahjongYakuKind.Tenhou, 13)

  private def checkChiihou(state: MahjongYakuCheckState): Vector[Yaku] =
    yakuIf(state.context.chiihou, MahjongYakuKind.Chiihou, 13)

  private def isKokushi(counts: Array[Int]): Boolean =
    val yaochu = Vector(Man1, Man9, Pin1, Pin9, Sou1, Sou9, Ton, Nan, Sha, Pei, Haku, Hatsu, Chun)
    yaochu.forall(counts(_) >= 1) && yaochu.map(counts).sum == 14 && yaochu.exists(counts(_) == 2)

  private def isChuuren(counts: Array[Int]): Boolean =
    Vector(Man1, Pin1, Sou1).exists { start =>
      val suitCounts = (0 until 9).map(offset => counts(start + offset))
      val otherTilesEmpty = counts.indices.forall { index =>
        (index >= start && index < start + 9) || counts(index) == 0
      }
      otherTilesEmpty &&
        suitCounts.head >= 3 &&
        suitCounts.last >= 3 &&
        suitCounts.slice(1, 8).forall(_ >= 1) &&
        suitCounts.sum == 14
    }

  private def isPureChuurenWait(counts: Array[Int], lastIndex: Int): Boolean =
    val temp = counts.clone()
    if lastIndex >= 0 && lastIndex < temp.length then temp(lastIndex) -= 1
    Vector(Man1, Pin1, Sou1).exists { start =>
      val pattern = Vector(3, 1, 1, 1, 1, 1, 1, 1, 3)
      pattern.indices.forall(offset => temp(start + offset) == pattern(offset))
    }

  private def isGreen(index: Int): Boolean =
    index == Hatsu ||
      (index >= Sou1 && index <= Sou9 && Set(2, 3, 4, 6, 8).contains(index - Sou1 + 1))
