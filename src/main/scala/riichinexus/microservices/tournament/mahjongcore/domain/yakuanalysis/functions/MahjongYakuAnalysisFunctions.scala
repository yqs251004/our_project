package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongMeldType}
import riichinexus.microservices.tournament.objects.paifumanagement.*
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

object MahjongYakuAnalysisFunctions:

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
      val yakuman = yakumanYaku(allCounts, context, fixedMelds)
      val yaku =
        if yakuman.nonEmpty then yakuman
        else ordinaryYaku(concealedCounts, allCounts, context, fixedMelds, closedHand)

      if yaku.isEmpty then None
      else
        val yakuWithDora =
          if yakuman.nonEmpty then yaku
          else addDora(yaku, allTiles, context)
        val han = yakuWithDora.map(_.han).sum
        val fu = calculateFu(concealedCounts, context, fixedMelds, closedHand, yakuWithDora)
        val pointResult = calculatePointResult(han, fu, context)
        Some(
          AgariResult(
            outcome = if context.target.isDefined then HandOutcome.Ron else HandOutcome.Tsumo,
            winner = Some(context.winner),
            target = context.target,
            han = Some(han),
            fu = Some(fu),
            yaku = yakuWithDora,
            points = pointResult.points,
            scoreChanges = pointResult.scoreChanges,
            doraIndicators = Some(context.doraIndicators),
            uraDoraIndicators = Option.when(context.riichi || context.doubleRiichi)(context.uraDoraIndicators),
            uraDoraVisible = Some(context.riichi || context.doubleRiichi),
            settlement = Some(
              RoundSettlement(
                notes = Vector(limitName(han, fu)).filter(_.nonEmpty)
              )
            )
          )
        )

  def isWinning(context: MahjongWinContext): Boolean =
    analyzeWin(context).nonEmpty

  private final case class PointResult(points: Int, scoreChanges: Vector[ScoreChange])

  private def yakumanYaku(
      counts: Array[Int],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld]
  ): Vector[Yaku] =
    val builder = Vector.newBuilder[Yaku]
    val hasOpenMeld = context.melds.exists(meld => !meld.closed)
    val lastIndex = indexOf(context.winningTile)

    if fixedMelds.isEmpty && isKokushi(counts) then
      if counts(lastIndex) == 2 then builder += Yaku("国士无双十三面", 26)
      else builder += Yaku("国士无双", 13)

    if fixedMelds.isEmpty && isChuuren(counts) then
      if isPureChuurenWait(counts, lastIndex) then builder += Yaku("纯正九莲宝灯", 26)
      else builder += Yaku("九莲宝灯", 13)

    if counts.indices.exists(index => counts(index) > 0) && counts.indices.forall(index => counts(index) == 0 || isHonor(index)) then
      builder += Yaku("字一色", 13)

    if counts.indices.exists(index => counts(index) > 0) && counts.indices.forall(index => counts(index) == 0 || isGreen(index)) then
      builder += Yaku("绿一色", 13)

    if counts.indices.exists(index => counts(index) > 0) && counts.indices.forall(index => counts(index) == 0 || isTerminal(index)) then
      builder += Yaku("清老头", 13)

    MahjongHandAnalysisFunctions.standardDecomposition(counts, fixedMelds).foreach { decomposition =>
      val tripletLike = decomposition.melds.filter(_.meldType != MahjongHandMeldType.Shuntsu)
      val dragonTriplets = tripletLike.count(meld => meld.tileIndex >= Haku && meld.tileIndex <= Chun)
      val windTriplets = tripletLike.count(meld => meld.tileIndex >= Ton && meld.tileIndex <= Pei)
      val kanCount = decomposition.melds.count(_.meldType == MahjongHandMeldType.Kantsu)
      val concealedTriplets = tripletLike.count(_.concealed)

      if !hasOpenMeld && concealedTriplets == 4 then
        if decomposition.pairIndex == lastIndex then builder += Yaku("四暗刻单骑", 26)
        else builder += Yaku("四暗刻", 13)
      if dragonTriplets == 3 then builder += Yaku("大三元", 13)
      if windTriplets == 4 then builder += Yaku("大四喜", 26)
      else if windTriplets == 3 && decomposition.pairIndex >= Ton && decomposition.pairIndex <= Pei then
        builder += Yaku("小四喜", 13)
      if kanCount == 4 then builder += Yaku("四杠子", 13)
    }

    if context.tenhou then builder += Yaku("天和", 13)
    builder.result()

  private def ordinaryYaku(
      concealedCounts: Array[Int],
      allCounts: Array[Int],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Vector[Yaku] =
    val builder = Vector.newBuilder[Yaku]
    val allTileIndices = allCounts.indices.filter(allCounts(_) > 0).toVector

    val isChiitoitsu = fixedMelds.isEmpty && allCounts.count(_ == 2) == 7
    if isChiitoitsu then builder += Yaku("七对子", 2)

    MahjongHandAnalysisFunctions.standardDecomposition(concealedCounts, fixedMelds).foreach { decomposition =>
      val shuntsu = decomposition.melds.filter(_.meldType == MahjongHandMeldType.Shuntsu)
      val tripletLike = decomposition.melds.filter(_.meldType != MahjongHandMeldType.Shuntsu)
      val seatWind = context.seatByPlayer.getOrElse(context.winner, SeatWind.East)

      if context.target.isEmpty && closedHand then builder += Yaku("门前清自摸和", 1)
      if context.doubleRiichi && closedHand then builder += Yaku("双立直", 2)
      else if context.riichi && closedHand then builder += Yaku("立直", 1)
      if context.ippatsu && closedHand then builder += Yaku("一发", 1)
      if context.rinshan then builder += Yaku("岭上开花", 1)
      if context.haitei && context.target.isEmpty then builder += Yaku("海底捞月", 1)
      if context.houtei && context.target.nonEmpty then builder += Yaku("河底捞鱼", 1)

      if allTileIndices.forall(isSimple) && (closedHand || context.ruleset.openTanyao) then
        builder += Yaku("断幺九", 1)

      tripletLike.foreach { meld =>
        if meld.tileIndex == Haku then builder += Yaku("役牌:白", 1)
        if meld.tileIndex == Hatsu then builder += Yaku("役牌:发", 1)
        if meld.tileIndex == Chun then builder += Yaku("役牌:中", 1)
        if meld.tileIndex == windToIndex(context.roundWind) then builder += Yaku("场风牌", 1)
        if meld.tileIndex == windToIndex(seatWind) then builder += Yaku("自风牌", 1)
      }

      val pinfu =
        closedHand &&
          shuntsu.size == 4 &&
          !isYakuhaiPair(decomposition.pairIndex, context.roundWind, seatWind)
      if pinfu then builder += Yaku("平和", 1)

      if closedHand then
        val pairCount = shuntsu.map(_.tileIndex).groupBy(identity).values.count(_.size >= 2)
        if pairCount >= 2 then builder += Yaku("二杯口", 3)
        else if pairCount == 1 then builder += Yaku("一杯口", 1)

      if shuntsu.isEmpty then builder += Yaku("对对和", 2)
      if tripletLike.count(_.concealed) == 3 then builder += Yaku("三暗刻", 2)
      if decomposition.melds.count(_.meldType == MahjongHandMeldType.Kantsu) == 3 then builder += Yaku("三杠子", 2)

      val dragonTriplets = tripletLike.count(meld => meld.tileIndex >= Haku && meld.tileIndex <= Chun)
      if dragonTriplets == 2 && decomposition.pairIndex >= Haku && decomposition.pairIndex <= Chun then
        builder += Yaku("小三元", 2)

      addSequencePatternYaku(builder, shuntsu, closedHand)
      addTerminalAndSuitYaku(builder, allTileIndices, decomposition, closedHand)
    }

    builder.result().distinct

  private def addDora(
      yaku: Vector[Yaku],
      allTiles: Vector[PaifuTile],
      context: MahjongWinContext
  ): Vector[Yaku] =
    val omote = countDora(allTiles, context.doraIndicators)
    val red = redDoraCount(allTiles)
    val ura =
      if context.riichi || context.doubleRiichi then countDora(allTiles, context.uraDoraIndicators)
      else 0
    yaku ++
      Option.when(omote > 0)(Yaku("宝牌", omote)).toVector ++
      Option.when(red > 0)(Yaku("红宝牌", red)).toVector ++
      Option.when(ura > 0)(Yaku("里宝牌", ura)).toVector

  private def addSequencePatternYaku(
      builder: scala.collection.mutable.Builder[Yaku, Vector[Yaku]],
      shuntsu: Vector[MahjongHandMeld],
      closedHand: Boolean
  ): Unit =
    val starts = shuntsu.map(_.tileIndex).toSet
    (0 to 6).foreach { start =>
      if starts.contains(Man1 + start) && starts.contains(Pin1 + start) && starts.contains(Sou1 + start) then
        builder += Yaku("三色同顺", if closedHand then 2 else 1)
    }
    Vector(Man1, Pin1, Sou1).foreach { suitStart =>
      if starts.contains(suitStart) && starts.contains(suitStart + 3) && starts.contains(suitStart + 6) then
        builder += Yaku("一气通贯", if closedHand then 2 else 1)
    }

  private def addTerminalAndSuitYaku(
      builder: scala.collection.mutable.Builder[Yaku, Vector[Yaku]],
      allTileIndices: Vector[Int],
      decomposition: MahjongHandDecomposition,
      closedHand: Boolean
  ): Unit =
    val hasHonor = allTileIndices.exists(isHonor)
    val suitCount =
      Vector(
        allTileIndices.exists(index => index >= Man1 && index <= Man9),
        allTileIndices.exists(index => index >= Pin1 && index <= Pin9),
        allTileIndices.exists(index => index >= Sou1 && index <= Sou9)
      ).count(identity)

    if suitCount == 1 && !hasHonor then builder += Yaku("清一色", if closedHand then 6 else 5)
    else if suitCount == 1 && hasHonor then builder += Yaku("混一色", if closedHand then 3 else 2)

    if allTileIndices.forall(isYaochu) then builder += Yaku("混老头", 2)

    val everyMeldHasTerminal =
      decomposition.melds.forall {
        case MahjongHandMeld(MahjongHandMeldType.Shuntsu, start, _) => start % 9 == 0 || start % 9 == 6
        case MahjongHandMeld(_, index, _) => isYaochu(index)
      }
    if everyMeldHasTerminal && isYaochu(decomposition.pairIndex) then
      if !hasHonor && decomposition.melds.exists(_.meldType == MahjongHandMeldType.Shuntsu) then
        builder += Yaku("纯全带幺九", if closedHand then 3 else 2)
      else if hasHonor then builder += Yaku("混全带幺九", if closedHand then 2 else 1)

    val tripletStarts = decomposition.melds.filter(_.meldType != MahjongHandMeldType.Shuntsu).map(_.tileIndex).toSet
    (0 to 8).foreach { rank =>
      if tripletStarts.contains(Man1 + rank) && tripletStarts.contains(Pin1 + rank) && tripletStarts.contains(Sou1 + rank) then
        builder += Yaku("三色同刻", 2)
    }

  private def calculateFu(
      concealedCounts: Array[Int],
      context: MahjongWinContext,
      fixedMelds: Vector[MahjongHandMeld],
      closedHand: Boolean,
      yaku: Vector[Yaku]
  ): Int =
    if yaku.exists(_.name == "七对子") then 25
    else
      val decomposition = MahjongHandAnalysisFunctions.standardDecomposition(concealedCounts, fixedMelds)
      val isPinfu = yaku.exists(_.name == "平和")
      if isPinfu && context.target.isEmpty then 20
      else
        val seatWind = context.seatByPlayer.getOrElse(context.winner, SeatWind.East)
        var fu = 20
        if context.target.isEmpty then fu += 2
        if context.target.nonEmpty && closedHand then fu += 10
        decomposition.foreach { hand =>
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

  private def calculatePointResult(han: Int, fu: Int, context: MahjongWinContext): PointResult =
    val basic = basicPoints(han, fu)
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

  private def basicPoints(han: Int, fu: Int): Int =
    if han >= 13 then 8000 * (han / 13).max(1)
    else if han >= 11 then 6000
    else if han >= 8 then 4000
    else if han >= 6 then 3000
    else if han == 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70) then 2000
    else math.min(2000, fu * (1 << (han + 2)))

  private def limitName(han: Int, fu: Int): String =
    if han >= 13 then
      val multiple = (han / 13).max(1)
      if multiple == 1 then "役满" else if multiple == 2 then "双倍役满" else s"${multiple}倍役满"
    else if han >= 11 then "三倍满"
    else if han >= 8 then "倍满"
    else if han >= 6 then "跳满"
    else if han == 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70) then "满贯"
    else ""

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
