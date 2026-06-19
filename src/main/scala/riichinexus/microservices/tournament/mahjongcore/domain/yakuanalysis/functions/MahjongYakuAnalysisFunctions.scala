package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongRulesetFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.{MahjongHandDecomposition, MahjongHandMeld, MahjongHandMeldType}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{Chun, Haku, Nan, Pei, Sha, Ton, countsOf, indexOf, isYaochu}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongMeldType}
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, AgariWinResult, HandOutcome, MahjongYakuKind, PaifuTile, RoundSettlement, RoundSettlementNote, ScoreChange, Yaku}
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** MahjongYakuAnalysisFunctions 提供麻将役种分析相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongYakuAnalysisFunctions:

  def analyzeWin(context: MahjongWinContext): Option[AgariResult] =
    val concealedTiles =
      if context.handTiles.size % 3 == 1 then context.handTiles :+ context.winningTile
      else context.handTiles
    val fixedMelds = context.melds.flatMap(toHandMeld)
    val concealedCounts = countsOf(concealedTiles)
    val allTiles = concealedTiles ++ context.melds.flatMap(_.tiles)
    val allCounts = countsOf(allTiles)
    val closedHand = context.melds.forall(_.closed)
    val allowSpecialHands = fixedMelds.isEmpty

    if !MahjongHandAnalysisFunctions.isWinning(concealedCounts, fixedMelds.size, allowSpecialHands) then None
    else
      val yakuman =
        MahjongYakuCheckFunctions.yakumanYaku(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
      val scoredResult =
        if yakuman.nonEmpty then
          scoreCandidate(yakuman, decomposition = None, concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand, includeDora = false)
        else
          MahjongYakuCheckFunctions.ordinaryYakuCandidates(concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
          .flatMap(candidate => scoreCandidate(candidate.yaku, candidate.decomposition, concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand, includeDora = true))
          .sortBy(scored => (scored.pointResult.points, scored.han, scored.fu))
          .lastOption

      if scoredResult.isEmpty then None
      else
        val scored = scoredResult.get
        val outcome = if context.target.isDefined then HandOutcome.Ron else HandOutcome.Tsumo
        val uraDoraIndicators = Option.when(context.riichi || context.doubleRiichi)(context.uraDoraIndicators)
        val uraDoraVisible = Some(context.riichi || context.doubleRiichi)
        val win = AgariWinResult(
          winner = context.winner,
          target = context.target,
          han = Some(scored.han),
          fu = Some(scored.fu),
          yaku = scored.yaku,
          points = scored.pointResult.points,
          doraIndicators = Some(context.doraIndicators),
          uraDoraIndicators = uraDoraIndicators,
          uraDoraVisible = uraDoraVisible
        )
        Some(
          AgariResult(
            outcome = outcome,
            winner = Some(context.winner),
            target = context.target,
            han = Some(scored.han),
            fu = Some(scored.fu),
            yaku = scored.yaku,
            points = scored.pointResult.points,
            scoreChanges = scored.pointResult.scoreChanges,
            doraIndicators = Some(context.doraIndicators),
            uraDoraIndicators = uraDoraIndicators,
            uraDoraVisible = uraDoraVisible,
            settlement = Some(
              RoundSettlement(
                notes = limitNote(scored.han, scored.fu, context.ruleset.allowMultipleYakuman).toVector
              )
            ),
            wins = Vector(win)
          )
        )

  def isWinning(context: MahjongWinContext): Boolean =
    analyzeWin(context).nonEmpty

  private final case class ScoredYakuCandidate(
      yaku: Vector[Yaku],
      han: Int,
      fu: Int,
      pointResult: PointResult
  )

  private final case class PointResult(points: Int, scoreChanges: Vector[ScoreChange])

  private def scoreCandidate(
      yaku: Vector[Yaku],
      decomposition: Option[MahjongHandDecomposition],
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean,
      includeDora: Boolean
  ): Option[ScoredYakuCandidate] =
    if yaku.isEmpty then None
    else
      val baseHan = yaku.map(_.han).sum
      if baseHan < MahjongRulesetFunctions.normalizedMinHan(context.ruleset) then None
      else
        val yakuWithDora =
          if includeDora then MahjongYakuCheckFunctions.addDora(yaku, concealedCounts, allCounts, allTiles, context, fixedMelds, closedHand)
          else yaku
        val han = yakuWithDora.map(_.han).sum
        val fu = calculateFu(concealedCounts, context, fixedMelds, closedHand, yakuWithDora, decomposition)
        val pointResult = calculatePointResult(han, fu, context)
        Some(ScoredYakuCandidate(yakuWithDora, han, fu, pointResult))

  private def calculateFu(
      concealedCounts: Array[Int],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean,
      yaku: Vector[Yaku],
      decomposition: Option[MahjongHandDecomposition]
  ): Int =
    if hasYaku(yaku, MahjongYakuKind.Chiitoitsu) then 25
    else
      val selectedDecomposition = decomposition.orElse(MahjongHandAnalysisFunctions.standardDecomposition(concealedCounts, fixedMelds))
      val isPinfu = hasYaku(yaku, MahjongYakuKind.Pinfu)
      if isPinfu && context.target.isEmpty then 20
      else
        val seatWind = context.seatByPlayer.getOrElse(context.winner, SeatWind.East)
        var fu = 20
        if context.target.isEmpty then fu += 2
        if context.target.nonEmpty && closedHand then fu += 10
        selectedDecomposition.foreach { hand =>
          if isYakuhaiPair(hand.pairIndex, context.roundWind, seatWind) then
            fu += 2
            if windToIndex(context.roundWind) == hand.pairIndex && windToIndex(seatWind) == hand.pairIndex then fu += 2
          hand.melds.foreach {
            case MahjongHandMeld(MahjongHandMeldType.Koutsu, tile, concealed) =>
              val base = if isYaochu(tile) then 4 else 2
              fu += base * (if concealed then 2 else 1)
            case MahjongHandMeld(MahjongHandMeldType.Kantsu, tile, concealed) =>
              val base = if isYaochu(tile) then 16 else 8
              fu += base * (if concealed then 2 else 1)
            case _ => ()
          }
        }
        roundUpTo10(math.max(fu, 30))

  private def hasYaku(yaku: Vector[Yaku], kind: MahjongYakuKind): Boolean =
    yaku.exists(_.kind == kind)

  private def calculatePointResult(han: Int, fu: Int, context: MahjongWinContext): PointResult =
    val basic = basicPoints(han, fu, context.ruleset.allowMultipleYakuman)
    val winnerIsDealer = context.seatByPlayer.get(context.winner).contains(SeatWind.East)
    val players = context.seatByPlayer.keys.toVector
    val changes =
      context.target match
        case Some(target) =>
          val payment = roundUpTo100(basic * (if winnerIsDealer then 6 else 4))
          players.map { playerId =>
            val delta =
              if playerId == context.winner then payment
              else if playerId == target then -payment
              else 0
            ScoreChange(playerId, delta)
          }
        case None =>
          val payments = players.filterNot(_ == context.winner).map { playerId =>
            val payerIsDealer = context.seatByPlayer.get(playerId).contains(SeatWind.East)
            playerId -> roundUpTo100(basic * (if winnerIsDealer || payerIsDealer then 2 else 1))
          }.toMap
          val total = payments.values.sum
          players.map { playerId =>
            val delta =
              if playerId == context.winner then total
              else -payments.getOrElse(playerId, 0)
            ScoreChange(playerId, delta)
          }
    PointResult(changes.find(_.playerId == context.winner).map(_.delta).getOrElse(0), changes)

  private def basicPoints(han: Int, fu: Int, allowMultipleYakuman: Boolean): Int =
    if han >= 13 then
      if allowMultipleYakuman then 8000 * (han / 13).max(1) else 8000
    else if han >= 11 then 6000
    else if han >= 8 then 4000
    else if han >= 6 then 3000
    else if han == 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70) then 2000
    else math.min(2000, fu * (1 << (han + 2)))

  private def limitNote(han: Int, fu: Int, allowMultipleYakuman: Boolean): Option[RoundSettlementNote] =
    if han >= 13 then
      val multiple = if allowMultipleYakuman then (han / 13).max(1) else 1
      Some(yakumanLimitNote(multiple))
    else if han >= 11 then Some(RoundSettlementNote.Sanbaiman)
    else if han >= 8 then Some(RoundSettlementNote.Baiman)
    else if han >= 6 then Some(RoundSettlementNote.Haneman)
    else if han == 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70) then Some(RoundSettlementNote.Mangan)
    else None

  private def yakumanLimitNote(multiplier: Int): RoundSettlementNote =
    multiplier match
      case 1 => RoundSettlementNote.Yakuman
      case 2 => RoundSettlementNote.DoubleYakuman
      case 3 => RoundSettlementNote.TripleYakuman
      case 4 => RoundSettlementNote.QuadrupleYakuman
      case 5 => RoundSettlementNote.QuintupleYakuman
      case 6 => RoundSettlementNote.SextupleYakuman
      case 7 => RoundSettlementNote.SeptupleYakuman
      case 8 => RoundSettlementNote.OctupleYakuman
      case _ => RoundSettlementNote.NonupleYakuman

  private def toHandMeld(meld: MahjongMeld): Option[MahjongHandMeld] =
    val tileIndex = meld.calledTile.orElse(meld.tiles.headOption).map(indexOf)
    tileIndex.map { index =>
      val meldType =
        meld.meldType match
          case MahjongMeldType.Chi => MahjongHandMeldType.Shuntsu
          case MahjongMeldType.Pon => MahjongHandMeldType.Koutsu
          case MahjongMeldType.OpenKan | MahjongMeldType.ClosedKan | MahjongMeldType.AddedKan =>
            MahjongHandMeldType.Kantsu
      val startIndex =
        if meld.meldType == MahjongMeldType.Chi then meld.tiles.map(indexOf).min
        else index
      MahjongHandMeld(
        meldType = meldType,
        tileIndex = startIndex,
        concealed = meld.closed || meld.meldType == MahjongMeldType.ClosedKan
      )
    }

  private def isYakuhaiPair(pairIndex: Int, roundWind: SeatWind, seatWind: SeatWind): Boolean =
    (pairIndex >= Haku && pairIndex <= Chun) ||
      pairIndex == windToIndex(roundWind) ||
      pairIndex == windToIndex(seatWind)

  private def windToIndex(wind: SeatWind): Int =
    wind match
      case SeatWind.East => Ton
      case SeatWind.South => Nan
      case SeatWind.West => Sha
      case SeatWind.North => Pei

  private def roundUpTo10(value: Int): Int =
    if value == 25 then 25 else ((value + 9) / 10) * 10

  private def roundUpTo100(value: Int): Int =
    ((value + 99) / 100) * 100
