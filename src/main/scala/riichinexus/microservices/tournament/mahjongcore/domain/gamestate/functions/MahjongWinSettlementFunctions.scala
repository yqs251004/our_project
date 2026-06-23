package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongPendingCallState, MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{indexOf, isYaochu}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongLegalAction
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongRoundPhase, MahjongTableStatus, MahjongTableSticks}
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, AgariWinResult, HandOutcome, MahjongYakuKind, PaifuTile, RoundSettlement, RoundSettlementNote, ScoreChange}
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import MahjongGameStateSupport.{aggregateScoreChanges, applyScoreChanges, nextSequenceNo, replaceSeat, requireRound, ronWinnerIdsBySeatOrder, seatByPlayerId, singleWinFromResult}

/** MahjongWinSettlementFunctions 提供麻将和牌结算相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongWinSettlementFunctions:
  private[mahjongcore] def declareTsumo(state: MahjongTableState, playerId: PlayerId): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, playerId)
    val winningTile = seat.drawTile.getOrElse(throw IllegalArgumentException("Tsumo requires a drawn tile"))
    val result = MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, playerId, target = None, winningTile = winningTile))
      .getOrElse(throw IllegalArgumentException("Submitted tsumo is not a winning hand"))
    finishRoundWithWin(state, round, playerId, target = None, winningTile, result)

  private[mahjongcore] def declareRon(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("Ron requires a pending discard"))
    MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, playerId, Some(pending.discardPlayerId), pending.tile))
      .getOrElse(throw IllegalArgumentException("Submitted ron is not a winning hand"))
    val acceptedRonPlayerIds = (pending.acceptedRonPlayerIds :+ playerId).distinct
    val remainingRonCandidates = pending.candidates
      .filterNot(_.playerId == playerId)
      .filter(MahjongCallActionFunctions.hasRonAction)
    val updatedPending = pending.copy(
      candidates = remainingRonCandidates,
      acceptedRonPlayerIds = acceptedRonPlayerIds
    )
    if state.ruleset.tripleRonAbortiveDraw && acceptedRonPlayerIds.size >= 3 then
      finishRoundWithAbortiveDraw(
        state,
        round.copy(pendingCall = Some(updatedPending)),
        Vector(RoundSettlementNote.TripleRonAbortiveDraw),
        acceptedEvent = None
      )
    else if !state.ruleset.doubleRon || remainingRonCandidates.isEmpty then
      finishRoundWithRonWinners(state, round.copy(pendingCall = Some(updatedPending)), updatedPending, acceptedEvent = None)
    else
      state.copy(
        currentRound = Some(round.copy(pendingCall = Some(updatedPending))),
        status = MahjongTableStatus.WaitingCallDecision
      ) -> None

  private[mahjongcore] def finishRoundWithWin(
      state: MahjongTableState,
      round: MahjongRoundState,
      winner: PlayerId,
      target: Option[PlayerId],
      winningTile: PaifuTile,
      result: AgariResult
  ): (MahjongTableState, Option[MahjongEvent]) =
    val settledResult = applyWinSettlementAdjustments(state, result, Vector(winner))
    val winEvent = MahjongEvent.WinDeclared(nextSequenceNo(round), winner, target, winningTile)
    val finishEvent = MahjongEvent.RoundFinished(nextSequenceNo(round) + 1, settledResult)
    val seatsAfterScore = applyScoreChanges(state.seats, settledResult.scoreChanges)
    val finishedRound = round.copy(
      phase = MahjongRoundPhase.Finished,
      pendingCall = None,
      events = round.events :+ winEvent :+ finishEvent,
      result = Some(settledResult)
    )
    state.copy(
      seats = seatsAfterScore,
      currentRound = Some(finishedRound),
      sticks = sticksAfterRoundResult(state, settledResult),
      status = MahjongTableStatus.RoundEnded
    ) -> Some(winEvent)

  private[mahjongcore] def finishRoundWithRonWinners(
      state: MahjongTableState,
      round: MahjongRoundState,
      pending: MahjongPendingCallState,
      acceptedEvent: Option[MahjongEvent]
  ): (MahjongTableState, Option[MahjongEvent]) =
    val winnerIds = ronWinnerIdsBySeatOrder(state, pending)
    require(winnerIds.nonEmpty, "Ron settlement requires at least one winner")
    val singleResults = winnerIds.map { winnerId =>
      MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, winnerId, Some(pending.discardPlayerId), pending.tile))
        .getOrElse(throw IllegalArgumentException(s"Accepted ron is not a winning hand for ${winnerId.value}"))
    }
    val wins = singleResults.flatMap(result => result.wins.headOption.orElse(singleWinFromResult(result)))
    val scoreChanges = aggregateScoreChanges(state.seats.map(_.playerId), singleResults.flatMap(_.scoreChanges))
    val primary = singleResults.head
    val notes =
      (if winnerIds.size == 2 then Vector(RoundSettlementNote.DoubleRon) else if winnerIds.size >= 3 then Vector(RoundSettlementNote.TripleRon) else Vector.empty) ++
        singleResults.flatMap(_.settlement.toVector.flatMap(_.notes)).distinct
    val baseResult = AgariResult(
      outcome = HandOutcome.Ron,
      winner = Some(winnerIds.head),
      target = Some(pending.discardPlayerId),
      han = primary.han,
      fu = primary.fu,
      yaku = primary.yaku,
      points = wins.map(_.points).sum,
      scoreChanges = scoreChanges,
      doraIndicators = primary.doraIndicators,
      uraDoraIndicators = primary.uraDoraIndicators,
      uraDoraVisible = primary.uraDoraVisible,
      settlement = Some(RoundSettlement(notes = notes)),
      wins = wins
    )
    val result = applyWinSettlementAdjustments(state, baseResult, winnerIds)
    val baseSequenceNo = nextSequenceNo(round)
    val winEvents = winnerIds.zipWithIndex.map { case (winnerId, index) =>
      MahjongEvent.WinDeclared(baseSequenceNo + index, winnerId, Some(pending.discardPlayerId), pending.tile)
    }
    val finishEvent = MahjongEvent.RoundFinished(baseSequenceNo + winEvents.size, result)
    val seatsAfterScore = applyScoreChanges(state.seats, result.scoreChanges)
    val finishedRound = round.copy(
      phase = MahjongRoundPhase.Finished,
      pendingCall = None,
      events = round.events ++ winEvents :+ finishEvent,
      result = Some(result)
    )
    state.copy(
      seats = seatsAfterScore,
      currentRound = Some(finishedRound),
      sticks = sticksAfterRoundResult(state, result),
      status = MahjongTableStatus.RoundEnded
    ) -> acceptedEvent.orElse(winEvents.headOption)

  private[mahjongcore] def finishRoundWithAbortiveDraw(
      state: MahjongTableState,
      round: MahjongRoundState,
      notes: Vector[RoundSettlementNote],
      acceptedEvent: Option[MahjongEvent]
  ): (MahjongTableState, Option[MahjongEvent]) =
    val result = drawResult(state, round, HandOutcome.AbortiveDraw, notes)
    val event = MahjongEvent.RoundFinished(nextSequenceNo(round), result)
    state.copy(
      seats = applyScoreChanges(state.seats, result.scoreChanges),
      currentRound = Some(round.copy(phase = MahjongRoundPhase.Finished, pendingCall = None, events = round.events :+ event, result = Some(result))),
      status = MahjongTableStatus.RoundEnded
    ) -> acceptedEvent.orElse(Some(event))

  private[mahjongcore] def abortiveDraw(state: MahjongTableState, note: Option[RoundSettlementNote]): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    finishRoundWithAbortiveDraw(state, round, note.toVector, acceptedEvent = None)
  private[mahjongcore] def winContext(
      state: MahjongTableState,
      winner: PlayerId,
      target: Option[PlayerId],
      winningTile: PaifuTile
  ): MahjongWinContext =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, winner)
    val handTiles =
      target match
        case Some(_) => seat.handTiles :+ winningTile
        case None => seat.handTiles ++ seat.drawTile.toVector
    val winnerIsDealer = seat.seat == SeatWind.East
    val winnerDrawCount = round.events.count {
      case MahjongEvent.TileDrawn(_, `winner`, _) => true
      case _ => false
    }
    val noCallsMade = !round.events.exists {
      case MahjongEvent.MeldCalled(_, _, _) => true
      case MahjongEvent.KanDeclared(_, _, _) => true
      case _ => false
    }
    MahjongWinContext(
      winner = winner,
      target = target,
      seatByPlayer = state.seats.map(seat => seat.playerId -> seat.seat).toMap,
      roundWind = round.descriptor.roundWind,
      handTiles = handTiles,
      melds = seat.melds,
      winningTile = winningTile,
      doraIndicators = round.doraIndicators,
      uraDoraIndicators = round.uraDoraIndicators.take(round.doraIndicators.size),
      riichi = seat.riichi,
      ippatsu = seat.ippatsu,
      rinshan = round.events.lastOption.exists {
        case MahjongEvent.TileDrawn(_, `winner`, tile) => round.deadWall.take(4).exists(deadTile => indexOf(deadTile) == indexOf(tile))
        case _ => false
      },
      haitei = round.wall.isEmpty && target.isEmpty,
      houtei = round.wall.isEmpty && target.nonEmpty,
      tenhou = winnerIsDealer && winnerDrawCount == 1 && target.isEmpty,
      chiihou = !winnerIsDealer && winnerDrawCount == 1 && target.isEmpty && noCallsMade,
      ruleset = state.ruleset
    )

  private[mahjongcore] def drawResult(
      state: MahjongTableState,
      round: MahjongRoundState,
      outcome: HandOutcome,
      notes: Vector[RoundSettlementNote]
  ): AgariResult =
    if outcome == HandOutcome.ExhaustiveDraw && state.ruleset.nagashiMangan then
      nagashiManganResult(state, round, notes).getOrElse(exhaustiveDrawResult(state, outcome, notes))
    else if outcome == HandOutcome.AbortiveDraw then abortiveDrawResult(state, notes)
    else exhaustiveDrawResult(state, outcome, notes)

  private[mahjongcore] def abortiveDrawResult(state: MahjongTableState, notes: Vector[RoundSettlementNote]): AgariResult =
    AgariResult(
      outcome = HandOutcome.AbortiveDraw,
      yaku = Vector.empty,
      points = 0,
      scoreChanges = state.seats.map(seat => ScoreChange(seat.playerId, 0)),
      settlement = Some(RoundSettlement(notes = notes))
    )

  private[mahjongcore] def exhaustiveDrawResult(state: MahjongTableState, outcome: HandOutcome, notes: Vector[RoundSettlementNote]): AgariResult =
    val tenpaiPlayers = state.seats.filter { seat =>
      MahjongHandAnalysisFunctions.calculateShanten(seat.handTiles, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0
    }.map(_.playerId)
    val scoreChanges = exhaustiveDrawScoreChanges(state.seats.map(_.playerId), tenpaiPlayers)
    AgariResult(
      outcome = outcome,
      yaku = Vector.empty,
      points = 0,
      scoreChanges = scoreChanges,
      tenpaiPlayerIds = Some(tenpaiPlayers),
      settlement = Some(RoundSettlement(notes = notes))
    )

  private[mahjongcore] def nagashiManganResult(
      state: MahjongTableState,
      round: MahjongRoundState,
      notes: Vector[RoundSettlementNote]
  ): Option[AgariResult] =
    val winners = state.seats.filter(isNagashiManganSeat)
    Option.when(winners.nonEmpty) {
      val players = state.seats.map(_.playerId)
      val seatByPlayer = state.seats.map(seat => seat.playerId -> seat.seat).toMap
      val wins = winners.map { seat =>
        val scoreChanges = manganTsumoScoreChanges(players, seatByPlayer, seat.playerId)
        AgariWinResult(
          winner = seat.playerId,
          han = Some(5),
          yaku = Vector(MahjongYakuKind.NagashiMangan.yaku(5)),
          points = scoreChanges.find(_.playerId == seat.playerId).map(_.delta).getOrElse(0),
          doraIndicators = Some(round.doraIndicators),
          uraDoraIndicators = None,
          uraDoraVisible = Some(false)
        )
      }
      val scoreChanges = aggregateScoreChanges(players, wins.flatMap(win => manganTsumoScoreChanges(players, seatByPlayer, win.winner)))
      val primary = wins.head
      AgariResult(
        outcome = HandOutcome.Tsumo,
        winner = Some(primary.winner),
        han = primary.han,
        fu = primary.fu,
        yaku = primary.yaku,
        points = wins.map(_.points).sum,
        scoreChanges = scoreChanges,
        doraIndicators = Some(round.doraIndicators),
        uraDoraIndicators = None,
        uraDoraVisible = Some(false),
        settlement = Some(RoundSettlement(notes = (notes :+ RoundSettlementNote.NagashiMangan).distinct)),
        wins = wins
      )
    }

  private[mahjongcore] def isNagashiManganSeat(seat: MahjongSeatState): Boolean =
    seat.melds.isEmpty &&
      seat.river.nonEmpty &&
      seat.river.forall(discard => isYaochu(indexOf(discard.tile)) && discard.calledBy.isEmpty)

  private[mahjongcore] def manganTsumoScoreChanges(
      players: Vector[PlayerId],
      seatByPlayer: Map[PlayerId, SeatWind],
      winner: PlayerId
  ): Vector[ScoreChange] =
    val winnerIsDealer = seatByPlayer.get(winner).contains(SeatWind.East)
    val payments = players.filterNot(_ == winner).map { playerId =>
      val payerIsDealer = seatByPlayer.get(playerId).contains(SeatWind.East)
      playerId -> (if winnerIsDealer || payerIsDealer then 4000 else 2000)
    }.toMap
    val total = payments.values.sum
    players.map { playerId =>
      if playerId == winner then ScoreChange(playerId, total)
      else ScoreChange(playerId, -payments.getOrElse(playerId, 0))
    }

  private[mahjongcore] def exhaustiveDrawScoreChanges(players: Vector[PlayerId], tenpaiPlayers: Vector[PlayerId]): Vector[ScoreChange] =
    if tenpaiPlayers.isEmpty || tenpaiPlayers.size == players.size then players.map(ScoreChange(_, 0))
    else
      val notenPlayers = players.filterNot(tenpaiPlayers.contains)
      val tenpaiGain = 3000 / tenpaiPlayers.size
      val notenLoss = 3000 / notenPlayers.size
      players.map { player =>
        if tenpaiPlayers.contains(player) then ScoreChange(player, tenpaiGain)
        else ScoreChange(player, -notenLoss)
      }

  private[mahjongcore] def acceptPendingRiichiDeclaration(state: MahjongTableState, pending: MahjongPendingCallState): MahjongTableState =
    state.seats
      .find(_.playerId == pending.discardPlayerId)
      .flatMap(_.river.find(_.sequenceNo == pending.discardSequenceNo))
      .fold(state)(discard => acceptRiichiDeclarationForDiscard(state, discard))

  private[mahjongcore] def acceptRiichiDeclarationForDiscard(state: MahjongTableState, discard: MahjongDiscard): MahjongTableState =
    if !discard.riichiDeclared then state
    else
      val declarer = seatByPlayerId(state, discard.playerId)
      val updatedDeclarer = declarer.copy(points = declarer.points - 1000)
      state.copy(
        seats = replaceSeat(state.seats, updatedDeclarer),
        sticks = state.sticks.copy(riichi = state.sticks.riichi + 1)
      )

  private[mahjongcore] def applyWinSettlementAdjustments(
      state: MahjongTableState,
      result: AgariResult,
      winnerIds: Vector[PlayerId]
  ): AgariResult =
    val players = state.seats.map(_.playerId)
    val riichiAward = state.sticks.riichi * 1000
    val honbaPayment = state.sticks.honba * 300
    val riichiAwardChanges =
      winnerIds.headOption.toVector.flatMap(winner => Option.when(riichiAward > 0)(ScoreChange(winner, riichiAward)))
    val honbaChanges = honbaSettlementChanges(state, result, winnerIds)
    val scoreChanges = aggregateScoreChanges(players, result.scoreChanges ++ riichiAwardChanges ++ honbaChanges)
    val settlement = mergeSettlement(result.settlement, riichiAward, honbaPayment)

    result.copy(scoreChanges = scoreChanges, settlement = Some(settlement))

  private[mahjongcore] def honbaSettlementChanges(
      state: MahjongTableState,
      result: AgariResult,
      winnerIds: Vector[PlayerId]
  ): Vector[ScoreChange] =
    if state.sticks.honba <= 0 then Vector.empty
    else
      val perRonWinner = state.sticks.honba * 300
      val perTsumoPayer = state.sticks.honba * 100
      result.outcome match
        case HandOutcome.Ron =>
          result.target.toVector.flatMap { target =>
            val winnerGains = winnerIds.map(winner => ScoreChange(winner, perRonWinner))
            winnerGains :+ ScoreChange(target, -perRonWinner * winnerIds.size)
          }
        case HandOutcome.Tsumo =>
          winnerIds.flatMap { winner =>
            val payers = state.seats.map(_.playerId).filterNot(_ == winner)
            ScoreChange(winner, perTsumoPayer * payers.size) +: payers.map(playerId => ScoreChange(playerId, -perTsumoPayer))
          }
        case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw => Vector.empty

  private[mahjongcore] def mergeSettlement(
      existing: Option[RoundSettlement],
      riichiAward: Int,
      honbaPayment: Int
  ): RoundSettlement =
    val base = existing.getOrElse(RoundSettlement())
    base.copy(
      riichiSticksDelta = riichiAward,
      honbaPayment = honbaPayment
    )

  private[mahjongcore] def sticksAfterRoundResult(state: MahjongTableState, result: AgariResult): MahjongTableSticks =
    result.outcome match
      case HandOutcome.Ron | HandOutcome.Tsumo => state.sticks.copy(riichi = 0)
      case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw => state.sticks
