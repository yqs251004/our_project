package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction, MahjongPublicEventView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.*
import riichinexus.microservices.tournament.objects.paifumanagement.*
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId, TableSeat}

object MahjongGameStateTransitionFunctions:

  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String): MahjongTableState =
    startTable(tableId, ruleset, seed, defaultTableSeats(tableId, ruleset))

  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String, tableSeats: Vector[TableSeat]): MahjongTableState =
    require(tableSeats.size == 4, "Mahjong table requires four seats")
    val orderedTableSeats = SeatWind.all.flatMap(wind => tableSeats.find(_.seat == wind))
    require(orderedTableSeats.size == 4, "Mahjong table seats must cover East, South, West and North")
    val shuffled = MahjongTileFunctions.shuffledWall(seed, ruleset)
    val deadSource = shuffled.takeRight(14)
    val deadWall = deadSource.take(4) ++ deadSource.slice(4, 9)
    val uraDora = deadSource.slice(9, 14)
    val liveWall = shuffled.dropRight(14)
    val playerIds = orderedTableSeats.map(_.playerId)

    var cursor = 0
    val initialHands = Array.fill(4)(Vector.empty[PaifuTile])
    (0 until 13).foreach { _ =>
      (0 until 4).foreach { seatIndex =>
        initialHands(seatIndex) = initialHands(seatIndex) :+ liveWall(cursor)
        cursor += 1
      }
    }
    val eastDraw = liveWall(cursor)
    cursor += 1

    val seats = orderedTableSeats.zipWithIndex.map { case (tableSeat, index) =>
      MahjongSeatState(
        seat = tableSeat.seat,
        playerId = tableSeat.playerId,
        points = ruleset.initialPoints,
        handTiles = sortTiles(initialHands(index)),
        drawTile = if tableSeat.seat == SeatWind.East then Some(eastDraw) else None,
        melds = Vector.empty,
        river = Vector.empty,
        riichi = false,
        ippatsu = false,
        furiten = false
      )
    }
    val descriptor = KyokuDescriptor(SeatWind.East, handNumber = 1, honba = 0)
    val round = MahjongRoundState(
      descriptor = descriptor,
      phase = MahjongRoundPhase.PlayerTurn,
      roundStartSticks = MahjongTableSticks(),
      wall = liveWall.drop(cursor),
      deadWall = deadWall,
      doraIndicators = Vector(deadSource(4)),
      uraDoraIndicators = uraDora,
      initialHands = orderedTableSeats.zipWithIndex.map { case (seat, index) => seat.playerId -> sortTiles(initialHands(index)) }.toMap,
      turnPlayerId = playerIds.head,
      pendingCall = None,
      events = Vector(
        MahjongEvent.TableStarted(1),
        MahjongEvent.RoundStarted(2, descriptor),
        MahjongEvent.TileDrawn(3, playerIds.head, eastDraw)
      ),
      result = None
    )
    MahjongTableState(
      tableId = tableId,
      ruleset = ruleset,
      status = MahjongTableStatus.WaitingPlayerAction,
      seats = seats,
      currentRound = Some(round),
      finishedRounds = Vector.empty,
      sticks = MahjongTableSticks(),
      version = 1
    )

  def notStartedTable(tableId: TableId, ruleset: MahjongRuleset = MahjongRuleset()): MahjongTableState =
    MahjongTableState(
      tableId = tableId,
      ruleset = ruleset,
      status = MahjongTableStatus.NotStarted,
      seats = SeatWind.all.map { seat =>
        MahjongSeatState(
          seat = seat,
          playerId = PlayerId(s"${tableId.value}-${SeatWind.toString(seat).toLowerCase}"),
          points = ruleset.initialPoints,
          handTiles = Vector.empty,
          drawTile = None,
          melds = Vector.empty,
          river = Vector.empty,
          riichi = false,
          ippatsu = false,
          furiten = false
        )
      },
      currentRound = None,
      finishedRounds = Vector.empty,
      sticks = MahjongTableSticks(),
      version = 0
    )

  def normalizeCurrentRoundState(state: MahjongTableState): MahjongTableState =
    state.currentRound match
      case None => state
      case Some(round) =>
        val riichiDiscardKeys = round.events.collect {
          case MahjongEvent.RiichiDeclared(sequenceNo, playerId, _) => (sequenceNo + 1, playerId)
        }.toSet
        val initialSeats = state.seats.map { seat =>
          seat.copy(
            handTiles = round.initialHands.getOrElse(seat.playerId, seat.handTiles),
            drawTile = None,
            melds = Vector.empty,
            river = Vector.empty,
            riichi = false,
            ippatsu = false,
            furiten = seat.furiten
          )
        }
        val rebuilt = round.events.foldLeft(state.copy(seats = initialSeats)) {
          case (current, MahjongEvent.TileDrawn(_, playerId, tile)) =>
            val seat = seatByPlayerId(current, playerId)
            current.copy(seats = replaceSeat(current.seats, seat.copy(drawTile = Some(tile))))

          case (current, MahjongEvent.TileDiscarded(sequenceNo, playerId, tile, tsumogiri)) =>
            val seat = seatByPlayerId(current, playerId)
            val normalizedTile = MahjongTileFunctions.normalize(tile)
            val updatedSeatWithoutDiscard =
              if tsumogiri then seat.copy(drawTile = None)
              else
                val updatedHand = MahjongTileFunctions.removeTiles(seat.handTiles, Vector(normalizedTile)).getOrElse(seat.handTiles)
                seat.copy(handTiles = sortTiles(updatedHand ++ seat.drawTile.toVector), drawTile = None)
            val riichiDeclared = riichiDiscardKeys.contains((sequenceNo, playerId))
            val discardView = MahjongDiscard(sequenceNo, playerId, normalizedTile, tsumogiri = tsumogiri, riichiDeclared = riichiDeclared)
            val discardedSeat = updatedSeatWithoutDiscard.copy(
              river = updatedSeatWithoutDiscard.river :+ discardView,
              riichi = updatedSeatWithoutDiscard.riichi || riichiDeclared,
              ippatsu = riichiDeclared,
              furiten = updatedSeatWithoutDiscard.furiten || isWinningOwnDiscard(updatedSeatWithoutDiscard, normalizedTile)
            )
            current.copy(seats = replaceSeat(current.seats, discardedSeat))

          case (current, MahjongEvent.MeldCalled(_, playerId, meld)) =>
            applyMeldEvent(current, playerId, meld)

          case (current, MahjongEvent.KanDeclared(_, playerId, meld)) =>
            applyMeldEvent(current, playerId, meld)

          case (current, MahjongEvent.RiichiDeclared(_, playerId, _)) =>
            val seat = seatByPlayerId(current, playerId)
            current.copy(seats = replaceSeat(current.seats, seat.copy(riichi = true, ippatsu = true)))

          case (current, _) => current
        }

        state.copy(seats = rebuilt.seats)

  def advanceRound(state: MahjongTableState, showcaseMode: Boolean = false): MahjongTableState =
    val normalized = normalizeCurrentRoundState(state)
    normalized.currentRound.filter(_.result.nonEmpty) match
      case None => normalized
      case Some(round) if normalized.status != MahjongTableStatus.RoundEnded => normalized
      case Some(round) =>
        val result = round.result.get
        val eastPlayerId = normalized.seats.find(_.seat == SeatWind.East).map(_.playerId)
        val dealerContinues =
          result.outcome match
            case HandOutcome.Ron | HandOutcome.Tsumo => eastPlayerId.exists(east => winningPlayerIds(result).contains(east))
            case HandOutcome.ExhaustiveDraw => eastPlayerId.exists(east => result.tenpaiPlayerIds.exists(_.contains(east)))
            case HandOutcome.AbortiveDraw => true
        val nextDescriptor =
          if dealerContinues then round.descriptor.copy(honba = round.descriptor.honba + 1)
          else nextDescriptorAfterDealerPass(round.descriptor, result.outcome)
        val nextSeats =
          if dealerContinues then normalized.seats
          else rotateSeatsForNextDealer(normalized.seats)
        val finishedRounds = normalized.finishedRounds :+ finishedRoundFromState(normalized, round)
        if shouldFinishTable(normalized, round, dealerContinues) then
          val finishEvent = MahjongEvent.TableFinished(nextSequenceNo(round), finalStandings(normalized))
          val finishedRound = round.copy(events = round.events :+ finishEvent)
          normalized.copy(
            status = MahjongTableStatus.Finished,
            currentRound = Some(finishedRound),
            finishedRounds = finishedRounds,
            version = normalized.version + 1
          )
        else
          val nextRoundState = dealRound(
            state = normalized,
            descriptor = nextDescriptor,
            seatsForRound = nextSeats,
            seed = s"mahjongcore:${normalized.tableId.value}:${normalized.version + 1}:${SeatWind.toString(nextDescriptor.roundWind)}:${nextDescriptor.handNumber}:${nextDescriptor.honba}",
            showcaseMode = showcaseMode
          )
          nextRoundState.copy(
            finishedRounds = finishedRounds,
            version = normalized.version + 1
          )

  def submitAction(
      state: MahjongTableState,
      action: MahjongSubmittedAction
  ): (MahjongTableState, Option[MahjongPublicEventView]) =
    val legalAction = legalActionsForPlayer(state, action.playerId).find(matchesSubmittedAction(_, action))
      .getOrElse(throw IllegalArgumentException(s"Illegal mahjong action ${action.commandType} for ${action.playerId.value}"))

    val (nextState, event) =
      action.commandType match
        case MahjongCommandType.Pass => passPendingCall(state, action.playerId)
        case MahjongCommandType.Discard => discard(state, action.playerId, action.tile.orElse(legalAction.tile).get, riichiDeclared = false)
        case MahjongCommandType.Riichi => discard(state, action.playerId, action.tile.orElse(legalAction.tile).get, riichiDeclared = true)
        case MahjongCommandType.Tsumo => declareTsumo(state, action.playerId)
        case MahjongCommandType.Ron => declareRon(state, action.playerId, legalAction)
        case MahjongCommandType.Chi | MahjongCommandType.Pon | MahjongCommandType.OpenKan => callMeld(state, action.playerId, legalAction)
        case MahjongCommandType.ClosedKan => closedKan(state, action.playerId, legalAction)
        case MahjongCommandType.AddedKan => addedKan(state, action.playerId, legalAction)
        case MahjongCommandType.AbortiveDraw => abortiveDraw(state, Some("abortive draw requested"))

    (nextState.copy(version = nextState.version + 1), event.map(eventToPublicView))

  def archiveTable(state: MahjongTableState): MahjongTableState =
    state.copy(status = MahjongTableStatus.Archived, version = state.version + 1)

  def toView(
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId],
      includeLegalActions: Boolean,
      revealAllHands: Boolean = false
  ): MahjongTableView =
    val legalActions =
      if includeLegalActions then
        viewerPlayerId match
          case Some(playerId) => legalActionsForPlayer(state, playerId)
          case None => Vector.empty
      else Vector.empty

    MahjongTableView(
      tableId = state.tableId,
      status = state.status,
      ruleset = state.ruleset,
      seats = state.seats.map(seatToView(_, state, viewerPlayerId, revealAllHands)),
      currentRound = state.currentRound.map(roundToView(_, state, viewerPlayerId)),
      legalActions = legalActions,
      finishedRoundCount = state.finishedRounds.size,
      lastEventSequenceNo = state.currentRound.flatMap(_.events.lastOption.map(sequenceNoOf)).getOrElse(0),
      lastEvent = state.currentRound.flatMap(_.events.lastOption.map(eventToPublicView)),
      version = state.version
    )

  def legalActionsForPlayer(state: MahjongTableState, playerId: PlayerId): Vector[MahjongLegalAction] =
    state.currentRound match
      case None => Vector.empty
      case Some(round) =>
        round.pendingCall.flatMap(_.candidates.find(_.playerId == playerId)) match
          case Some(candidate) =>
            val pending = round.pendingCall.get
            val activeActions = activeLegalActionsForCandidate(state, pending, candidate)
            if activeActions.isEmpty then Vector.empty
            else
              activeActions :+ MahjongLegalAction(
                commandType = MahjongCommandType.Pass,
                tile = Some(pending.tile),
                fromPlayerId = Some(pending.discardPlayerId),
                targetSequenceNo = Some(pending.discardSequenceNo),
                priority = 0
              )
          case None if round.turnPlayerId == playerId && round.phase == MahjongRoundPhase.PlayerTurn =>
            currentTurnActions(state, round, seatByPlayerId(state, playerId))
          case _ => Vector.empty

  private def currentTurnActions(
      state: MahjongTableState,
      round: MahjongRoundState,
      seat: MahjongSeatState
  ): Vector[MahjongLegalAction] =
    val discardTiles =
      if seat.riichi then seat.drawTile.toVector.map(MahjongTileFunctions.normalize)
      else (seat.handTiles ++ seat.drawTile.toVector).map(MahjongTileFunctions.normalize).distinctBy(tile => (indexOf(tile), isRed(tile)))
    val discardActions = discardTiles.map(tile => MahjongLegalAction(MahjongCommandType.Discard, tile = Some(tile), priority = 10))
    val riichiActions =
      if canDeclareRiichi(seat) then
        discardTiles.filter(tile => leavesTenpaiAfterDiscard(seat, tile)).map { tile =>
          MahjongLegalAction(MahjongCommandType.Riichi, tile = Some(tile), priority = 30)
        }
      else Vector.empty
    val tsumoActions =
      seat.drawTile.toVector.flatMap { tile =>
        val context = winContext(state, seat.playerId, target = None, winningTile = tile)
        Option.when(MahjongYakuAnalysisFunctions.isWinning(context))(
          MahjongLegalAction(MahjongCommandType.Tsumo, tile = Some(tile), priority = 100)
        )
      }
    val closedKanActions = closedKanLegalActions(seat)
    val addedKanActions = addedKanLegalActions(seat)
    tsumoActions ++ closedKanActions ++ addedKanActions ++ riichiActions ++ discardActions

  private def discard(
      state: MahjongTableState,
      playerId: PlayerId,
      tile: PaifuTile,
      riichiDeclared: Boolean
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, playerId)
    val normalizedTile = MahjongTileFunctions.normalize(tile)
    val fromDraw = seat.drawTile.exists(draw => sameTile(draw, normalizedTile))
    if seat.riichi && !riichiDeclared && !fromDraw then
      throw IllegalArgumentException(s"Player ${playerId.value} must discard the drawn tile after riichi")
    val updatedSeatWithoutDiscard =
      if fromDraw then seat.copy(drawTile = None)
      else
        val updatedHand = MahjongTileFunctions.removeTiles(seat.handTiles, Vector(normalizedTile))
          .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} does not have tile ${tile.value}"))
        seat.copy(handTiles = sortTiles(updatedHand ++ seat.drawTile.toVector), drawTile = None)

    val baseSequence = nextSequenceNo(round)
    val riichiEvent = Option.when(riichiDeclared)(MahjongEvent.RiichiDeclared(baseSequence, playerId, normalizedTile))
    val discardSequence = baseSequence + riichiEvent.size
    val discardView = MahjongDiscard(discardSequence, playerId, normalizedTile, tsumogiri = fromDraw, riichiDeclared = riichiDeclared)
    val discardedSeat = updatedSeatWithoutDiscard.copy(
      river = updatedSeatWithoutDiscard.river :+ discardView,
      riichi = updatedSeatWithoutDiscard.riichi || riichiDeclared,
      ippatsu = riichiDeclared,
      furiten = updatedSeatWithoutDiscard.furiten || isWinningOwnDiscard(updatedSeatWithoutDiscard, normalizedTile)
    )
    val seats = replaceSeat(state.seats, discardedSeat)
    val discardEvent = MahjongEvent.TileDiscarded(discardSequence, playerId, normalizedTile, fromDraw)
    val roundWithDiscard = round.copy(events = round.events ++ riichiEvent.toVector :+ discardEvent)
    val pendingCall = buildPendingCall(state.copy(seats = seats), roundWithDiscard, discardView)

    pendingCall match
      case Some(pending) =>
        val nextRound = roundWithDiscard.copy(
          phase = MahjongRoundPhase.CallDecision,
          pendingCall = Some(pending)
        )
        val nextState = state.copy(
          seats = seats,
          currentRound = Some(nextRound),
          status = MahjongTableStatus.WaitingCallDecision
        )
        nextState -> Some(discardEvent)
      case None =>
        val stateWithAcceptedRiichi = acceptRiichiDeclarationForDiscard(state.copy(seats = seats), discardView)
        drawForNextPlayer(stateWithAcceptedRiichi, roundWithDiscard, nextSeatId(state, playerId)) -> Some(discardEvent)

  private def passPendingCall(state: MahjongTableState, playerId: PlayerId): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to pass"))
    val stateAfterRiichiFuriten = markRiichiMissedRonFuriten(state, pending, playerId)
    val event = MahjongEvent.PlayerPassed(nextSequenceNo(round), playerId)
    val remaining = pending.candidates.filterNot(_.playerId == playerId)
    val normalizedRemaining =
      if pending.acceptedRonPlayerIds.nonEmpty then remaining.filter(hasRonAction)
      else remaining
    if normalizedRemaining.nonEmpty then
      val nextRound = round.copy(
        pendingCall = Some(pending.copy(candidates = normalizedRemaining)),
        events = round.events :+ event
      )
      stateAfterRiichiFuriten.copy(currentRound = Some(nextRound), status = MahjongTableStatus.WaitingCallDecision) -> Some(event)
    else if pending.acceptedRonPlayerIds.nonEmpty then
      val nextRound = round.copy(
        pendingCall = Some(pending.copy(candidates = Vector.empty)),
        events = round.events :+ event
      )
      finishRoundWithRonWinners(stateAfterRiichiFuriten, nextRound, pending.copy(candidates = Vector.empty), Some(event))
    else
      val clearedRound = round.copy(pendingCall = None, events = round.events :+ event)
      val stateWithAcceptedRiichi = acceptPendingRiichiDeclaration(stateAfterRiichiFuriten, pending)
      drawForNextPlayer(stateWithAcceptedRiichi, clearedRound, nextSeatId(state, pending.discardPlayerId)) -> Some(event)

  private def callMeld(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to resolve"))
    val stateWithAcceptedRiichi = acceptPendingRiichiDeclaration(state, pending)
    val caller = seatByPlayerId(stateWithAcceptedRiichi, playerId)
    val discardTile = pending.tile
    val meldTiles = legalAction.tiles.nonEmpty match
      case true => legalAction.tiles.map(MahjongTileFunctions.normalize)
      case false => defaultMeldTiles(legalAction.commandType, discardTile)
    val handTilesToRemove = removeOneByIndex(meldTiles, indexOf(discardTile))
    val handAfterCall = MahjongTileFunctions.removeTiles(caller.handTiles, handTilesToRemove)
      .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} cannot call ${legalAction.commandType}"))
    val meld = MahjongMeld(
      meldType = legalAction.commandType match
        case MahjongCommandType.Chi => MahjongMeldType.Chi
        case MahjongCommandType.Pon => MahjongMeldType.Pon
        case MahjongCommandType.OpenKan => MahjongMeldType.OpenKan
        case other => throw IllegalArgumentException(s"Unsupported meld command: $other"),
      owner = playerId,
      fromPlayer = Some(pending.discardPlayerId),
      calledTile = Some(discardTile),
      tiles = sortTiles(meldTiles),
      closed = false
    )
    val sequenceNo = nextSequenceNo(round)
    val event = if legalAction.commandType == MahjongCommandType.OpenKan then MahjongEvent.KanDeclared(sequenceNo, playerId, meld) else MahjongEvent.MeldCalled(sequenceNo, playerId, meld)
    val callerAfterCall = caller.copy(handTiles = sortTiles(handAfterCall), melds = caller.melds :+ meld, ippatsu = false)
    val seatsAfterCall = replaceSeat(markDiscardCalledBy(stateWithAcceptedRiichi.seats, pending.discardPlayerId, pending.discardSequenceNo, playerId), callerAfterCall).map(_.copy(ippatsu = false))
    val baseRound = round.copy(
      pendingCall = None,
      turnPlayerId = playerId,
      phase = MahjongRoundPhase.PlayerTurn,
      events = round.events :+ event
    )
    val nextState = stateWithAcceptedRiichi.copy(seats = seatsAfterCall)
    if legalAction.commandType == MahjongCommandType.OpenKan then
      drawReplacementAfterKan(nextState, baseRound, callerAfterCall.playerId, event)
    else
      nextState.copy(currentRound = Some(baseRound), status = MahjongTableStatus.WaitingPlayerAction) -> Some(event)

  private def closedKan(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, playerId)
    val tile = legalAction.tile.orElse(legalAction.tiles.headOption).getOrElse(throw IllegalArgumentException("Closed kan needs a tile"))
    val kanIndex = indexOf(tile)
    val sourceTiles = seat.handTiles ++ seat.drawTile.toVector
    val handAfterKan = MahjongTileFunctions.removeTiles(sourceTiles, Vector.fill(4)(tileOf(kanIndex)))
      .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} cannot closed-kan ${tile.value}"))
    val meld = MahjongMeld(
      meldType = MahjongMeldType.ClosedKan,
      owner = playerId,
      tiles = Vector.fill(4)(tileOf(kanIndex)),
      closed = true
    )
    val event = MahjongEvent.KanDeclared(nextSequenceNo(round), playerId, meld)
    val seatAfterKan = seat.copy(handTiles = sortTiles(handAfterKan), drawTile = None, melds = seat.melds :+ meld, ippatsu = false)
    val nextState = state.copy(seats = replaceSeat(state.seats.map(_.copy(ippatsu = false)), seatAfterKan))
    val nextRound = round.copy(events = round.events :+ event, turnPlayerId = playerId, phase = MahjongRoundPhase.PlayerTurn)
    drawReplacementAfterKan(nextState, nextRound, playerId, event)

  private def addedKan(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, playerId)
    val tile = legalAction.tile.orElse(legalAction.tiles.headOption).getOrElse(throw IllegalArgumentException("Added kan needs a tile"))
    val kanIndex = indexOf(tile)
    val ponIndex = seat.melds.indexWhere(meld => meld.meldType == MahjongMeldType.Pon && meld.tiles.headOption.exists(t => indexOf(t) == kanIndex))
    if ponIndex < 0 then throw IllegalArgumentException(s"Player ${playerId.value} has no pon to upgrade")
    val sourceTiles = seat.handTiles ++ seat.drawTile.toVector
    val handAfterKan = MahjongTileFunctions.removeTiles(sourceTiles, Vector(tileOf(kanIndex)))
      .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} cannot added-kan ${tile.value}"))
    val upgraded = seat.melds(ponIndex).copy(meldType = MahjongMeldType.AddedKan, tiles = Vector.fill(4)(tileOf(kanIndex)))
    val melds = seat.melds.updated(ponIndex, upgraded)
    val event = MahjongEvent.KanDeclared(nextSequenceNo(round), playerId, upgraded)
    val seatAfterKan = seat.copy(handTiles = sortTiles(handAfterKan), drawTile = None, melds = melds, ippatsu = false)
    val nextState = state.copy(seats = replaceSeat(state.seats.map(_.copy(ippatsu = false)), seatAfterKan))
    val nextRound = round.copy(events = round.events :+ event, turnPlayerId = playerId, phase = MahjongRoundPhase.PlayerTurn)
    drawReplacementAfterKan(nextState, nextRound, playerId, event)

  private def declareTsumo(state: MahjongTableState, playerId: PlayerId): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val seat = seatByPlayerId(state, playerId)
    val winningTile = seat.drawTile.getOrElse(throw IllegalArgumentException("Tsumo requires a drawn tile"))
    val result = MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, playerId, target = None, winningTile = winningTile))
      .getOrElse(throw IllegalArgumentException("Submitted tsumo is not a winning hand"))
    finishRoundWithWin(state, round, playerId, target = None, winningTile, result)

  private def declareRon(
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
      .filter(hasRonAction)
    val updatedPending = pending.copy(
      candidates = remainingRonCandidates,
      acceptedRonPlayerIds = acceptedRonPlayerIds
    )
    if state.ruleset.tripleRonAbortiveDraw && acceptedRonPlayerIds.size >= 3 then
      finishRoundWithAbortiveDraw(
        state,
        round.copy(pendingCall = Some(updatedPending)),
        Vector("三家和流局"),
        acceptedEvent = None
      )
    else if !state.ruleset.doubleRon || remainingRonCandidates.isEmpty then
      finishRoundWithRonWinners(state, round.copy(pendingCall = Some(updatedPending)), updatedPending, acceptedEvent = None)
    else
      state.copy(
        currentRound = Some(round.copy(pendingCall = Some(updatedPending))),
        status = MahjongTableStatus.WaitingCallDecision
      ) -> None

  private def finishRoundWithWin(
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

  private def finishRoundWithRonWinners(
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
      (if winnerIds.size == 2 then Vector("双响") else if winnerIds.size >= 3 then Vector("三家荣和") else Vector.empty) ++
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

  private def finishRoundWithAbortiveDraw(
      state: MahjongTableState,
      round: MahjongRoundState,
      notes: Vector[String],
      acceptedEvent: Option[MahjongEvent]
  ): (MahjongTableState, Option[MahjongEvent]) =
    val result = drawResult(state, round, HandOutcome.AbortiveDraw, notes)
    val event = MahjongEvent.RoundFinished(nextSequenceNo(round), result)
    state.copy(
      seats = applyScoreChanges(state.seats, result.scoreChanges),
      currentRound = Some(round.copy(phase = MahjongRoundPhase.Finished, pendingCall = None, events = round.events :+ event, result = Some(result))),
      status = MahjongTableStatus.RoundEnded
    ) -> acceptedEvent.orElse(Some(event))

  private def abortiveDraw(state: MahjongTableState, note: Option[String]): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    finishRoundWithAbortiveDraw(state, round, note.toVector, acceptedEvent = None)

  private def drawForNextPlayer(state: MahjongTableState, round: MahjongRoundState, nextPlayerId: PlayerId): MahjongTableState =
    if round.wall.isEmpty then
      val result = drawResult(state, round, HandOutcome.ExhaustiveDraw, Vector("荒牌流局"))
      val event = MahjongEvent.RoundFinished(nextSequenceNo(round), result)
      state.copy(
        seats = applyScoreChanges(state.seats, result.scoreChanges),
        currentRound = Some(round.copy(phase = MahjongRoundPhase.Finished, pendingCall = None, events = round.events :+ event, result = Some(result))),
        status = MahjongTableStatus.RoundEnded
      )
    else
      val tile = round.wall.head
      val nextSeat = seatByPlayerId(state, nextPlayerId)
      val updatedSeat = nextSeat.copy(drawTile = Some(tile))
      val event = MahjongEvent.TileDrawn(nextSequenceNo(round), nextPlayerId, tile)
      state.copy(
        seats = replaceSeat(state.seats, updatedSeat),
        currentRound = Some(round.copy(
          wall = round.wall.tail,
          turnPlayerId = nextPlayerId,
          pendingCall = None,
          phase = MahjongRoundPhase.PlayerTurn,
          events = round.events :+ event
        )),
        status = MahjongTableStatus.WaitingPlayerAction
      )

  private def drawReplacementAfterKan(
      state: MahjongTableState,
      round: MahjongRoundState,
      playerId: PlayerId,
      acceptedEvent: MahjongEvent
  ): (MahjongTableState, Option[MahjongEvent]) =
    val usedReplacementCount = math.max(0, round.doraIndicators.size - 1)
    if usedReplacementCount >= 4 then throw IllegalArgumentException("No rinshan tile remains")
    val replacement = round.deadWall(usedReplacementCount)
    val doraIndex = 4 + round.doraIndicators.size
    val revealedDora = Option.when(doraIndex < round.deadWall.size)(round.deadWall(doraIndex))
    val seat = seatByPlayerId(state, playerId)
    val updatedSeat = seat.copy(drawTile = Some(replacement), ippatsu = false)
    val drawEvent = MahjongEvent.TileDrawn(nextSequenceNo(round), playerId, replacement)
    val doraEvent = revealedDora.map(tile => MahjongEvent.DoraRevealed(nextSequenceNo(round) + 1, tile))
    val nextRound = round.copy(
      doraIndicators = round.doraIndicators ++ revealedDora,
      turnPlayerId = playerId,
      phase = MahjongRoundPhase.PlayerTurn,
      events = (round.events :+ drawEvent) ++ doraEvent.toVector
    )
    state.copy(
      seats = replaceSeat(state.seats, updatedSeat),
      currentRound = Some(nextRound),
      status = MahjongTableStatus.WaitingPlayerAction
    ) -> Some(acceptedEvent)

  private def buildPendingCall(
      state: MahjongTableState,
      round: MahjongRoundState,
      discard: MahjongDiscard
  ): Option[MahjongPendingCallState] =
    val candidates = state.seats.filterNot(_.playerId == discard.playerId).flatMap { seat =>
      val ron = ronLegalAction(state, round, seat, discard)
      val actions =
        if seat.riichi then ron.toVector
        else
          val pon = ponLegalAction(seat, discard)
          val kan = openKanLegalAction(seat, discard)
          val chi = if seat.playerId == nextSeatId(state, discard.playerId) then chiLegalActions(seat, discard) else Vector.empty
          ron.toVector ++ kan.toVector ++ pon.toVector ++ chi
      Option.when(actions.nonEmpty)(MahjongCallCandidate(seat.playerId, actions))
    }
    Option.when(candidates.nonEmpty)(
      MahjongPendingCallState(discard.sequenceNo, discard.playerId, discard.tile, candidates)
    )

  private def activeLegalActionsForCandidate(
      state: MahjongTableState,
      pending: MahjongPendingCallState,
      candidate: MahjongCallCandidate
  ): Vector[MahjongLegalAction] =
    val highestPriority = pending.candidates
      .flatMap(_.legalActions.map(_.priority))
      .maxOption
      .getOrElse(0)
    val candidateHighestPriority = candidate.legalActions.map(_.priority).maxOption.getOrElse(0)
    val activeActions = candidate.legalActions.filter(_.priority == highestPriority)
    if candidateHighestPriority != highestPriority then Vector.empty
    else if highestPriority == 100 && !state.ruleset.doubleRon then
      closestRonCandidate(state, pending).filter(_ == candidate.playerId).fold(Vector.empty[MahjongLegalAction])(_ => activeActions)
    else if highestPriority == 100 then activeActions
    else candidate.legalActions

  private def hasRonAction(candidate: MahjongCallCandidate): Boolean =
    candidate.legalActions.exists(_.commandType == MahjongCommandType.Ron)

  private def closestRonCandidate(state: MahjongTableState, pending: MahjongPendingCallState): Option[PlayerId] =
    pending.candidates
      .filter(hasRonAction)
      .sortBy(candidate => seatDistanceFromDiscarder(state, pending.discardPlayerId, candidate.playerId))
      .headOption
      .map(_.playerId)

  private def ronLegalAction(
      state: MahjongTableState,
      round: MahjongRoundState,
      seat: MahjongSeatState,
      discard: MahjongDiscard
  ): Option[MahjongLegalAction] =
    if seat.furiten then None
    else
      val context = winContext(state, seat.playerId, Some(discard.playerId), discard.tile)
      Option.when(MahjongYakuAnalysisFunctions.isWinning(context))(
        MahjongLegalAction(
          commandType = MahjongCommandType.Ron,
          tile = Some(discard.tile),
          fromPlayerId = Some(discard.playerId),
          targetSequenceNo = Some(discard.sequenceNo),
          priority = 100
        )
      )

  private def markRiichiMissedRonFuriten(
      state: MahjongTableState,
      pending: MahjongPendingCallState,
      playerId: PlayerId
  ): MahjongTableState =
    val missedRon = pending.candidates.exists(candidate => candidate.playerId == playerId && hasRonAction(candidate))
    if !missedRon then state
    else
      val seat = seatByPlayerId(state, playerId)
      if !seat.riichi then state
      else state.copy(seats = replaceSeat(state.seats, seat.copy(furiten = true)))

  private def ponLegalAction(seat: MahjongSeatState, discard: MahjongDiscard): Option[MahjongLegalAction] =
    val index = indexOf(discard.tile)
    Option.when(countsOf(seat.handTiles)(index) >= 2)(
      MahjongLegalAction(
        MahjongCommandType.Pon,
        tile = Some(discard.tile),
        tiles = Vector.fill(3)(tileOf(index)),
        fromPlayerId = Some(discard.playerId),
        targetSequenceNo = Some(discard.sequenceNo),
        priority = 50
      )
    )

  private def openKanLegalAction(seat: MahjongSeatState, discard: MahjongDiscard): Option[MahjongLegalAction] =
    val index = indexOf(discard.tile)
    Option.when(countsOf(seat.handTiles)(index) >= 3)(
      MahjongLegalAction(
        MahjongCommandType.OpenKan,
        tile = Some(discard.tile),
        tiles = Vector.fill(4)(tileOf(index)),
        fromPlayerId = Some(discard.playerId),
        targetSequenceNo = Some(discard.sequenceNo),
        priority = 80
      )
    )

  private def chiLegalActions(seat: MahjongSeatState, discard: MahjongDiscard): Vector[MahjongLegalAction] =
    val index = indexOf(discard.tile)
    if !isSuited(index) then Vector.empty
    else
      val counts = countsOf(seat.handTiles)
      Vector(index - 2, index - 1, index).filter(start => start >= 0 && start % 9 <= 6 && index >= start && index <= start + 2).flatMap { start =>
        val needed = Vector(start, start + 1, start + 2).filterNot(_ == index)
        if needed.forall(tileIndex => counts(tileIndex) > 0) then
          tileChoiceCombinations(needed, seat.handTiles).map { handTiles =>
            MahjongLegalAction(
              MahjongCommandType.Chi,
              tile = Some(discard.tile),
              tiles = sortTiles(discard.tile +: handTiles),
              fromPlayerId = Some(discard.playerId),
              targetSequenceNo = Some(discard.sequenceNo),
              priority = 40
            )
          }
        else Vector.empty
      }

  private def closedKanLegalActions(seat: MahjongSeatState): Vector[MahjongLegalAction] =
    val counts = countsOf(seat.handTiles ++ seat.drawTile.toVector)
    (0 until TileTypeCount).toVector.flatMap { index =>
      if counts(index) == 4 && (!seat.riichi || riichiClosedKanKeepsWaits(seat, index)) then
        Vector(MahjongLegalAction(MahjongCommandType.ClosedKan, tile = Some(tileOf(index)), tiles = Vector.fill(4)(tileOf(index)), priority = 70))
      else Vector.empty
    }

  private def addedKanLegalActions(seat: MahjongSeatState): Vector[MahjongLegalAction] =
    val counts = countsOf(seat.handTiles ++ seat.drawTile.toVector)
    seat.melds.flatMap { meld =>
      if meld.meldType == MahjongMeldType.Pon then
        meld.tiles.headOption.toVector.flatMap { tile =>
          val index = indexOf(tile)
          Option.when(counts(index) >= 1)(
            MahjongLegalAction(MahjongCommandType.AddedKan, tile = Some(tileOf(index)), tiles = Vector.fill(4)(tileOf(index)), priority = 70)
          )
        }
      else Vector.empty
    }

  private def riichiClosedKanKeepsWaits(seat: MahjongSeatState, kanIndex: Int): Boolean =
    if !seat.drawTile.exists(tile => indexOf(MahjongTileFunctions.normalize(tile)) == kanIndex) then false
    else
      val counts = countsOf(seat.handTiles ++ seat.drawTile.toVector)
      val pre = counts.clone()
      pre(kanIndex) -= 1
      val preWaits = MahjongHandAnalysisFunctions.waitingTiles(tilesFromCounts(pre), seat.melds.size, allowSpecialHands = seat.melds.isEmpty).map(indexOf).toSet
      val post = counts.clone()
      post(kanIndex) -= 4
      val postWaits = MahjongHandAnalysisFunctions.waitingTiles(tilesFromCounts(post), seat.melds.size + 1, allowSpecialHands = false).map(indexOf).toSet
      preWaits == postWaits

  private def canDeclareRiichi(seat: MahjongSeatState): Boolean =
    !seat.riichi && seat.points >= 1000 && seat.melds.forall(_.closed)

  private def sameTile(left: PaifuTile, right: PaifuTile): Boolean =
    MahjongTileFunctions.normalize(left) == MahjongTileFunctions.normalize(right)

  private def tileChoiceCombinations(
      neededIndices: Vector[Int],
      handTiles: Vector[PaifuTile]
  ): Vector[Vector[PaifuTile]] =
    neededIndices.foldLeft(Vector(Vector.empty[PaifuTile])) { (combinations, tileIndex) =>
      val choices = handTileChoices(tileIndex, handTiles)
      combinations.flatMap(combination => choices.map(choice => combination :+ choice))
    }

  private def handTileChoices(tileIndex: Int, handTiles: Vector[PaifuTile]): Vector[PaifuTile] =
    handTiles
      .map(MahjongTileFunctions.normalize)
      .filter(tile => indexOf(tile) == tileIndex)
      .distinctBy(tile => isRed(tile))

  private val showcaseEast2InitialHands: Map[SeatWind, Vector[PaifuTile]] =
    Map(
      SeatWind.East -> showcaseTiles("1m", "9m", "1s", "9s", "1p", "9p", "1z", "2z", "3z", "4z", "5z", "6z", "7z"),
      SeatWind.South -> showcaseTiles("1p", "1p", "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p", "9p", "9p"),
      SeatWind.West -> showcaseTiles("1s", "1s", "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s", "9s", "9s"),
      SeatWind.North -> showcaseTiles("1m", "1m", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "9m", "9m")
    )
  private val showcaseEast2EastDraw: PaifuTile = PaifuTile("0p")
  private val showcaseEast2DoraIndicator: PaifuTile = PaifuTile("4z")

  private def showcaseTiles(values: String*): Vector[PaifuTile] =
    values.toVector.map(PaifuTile(_))

  private def showcaseWallForRound(
      ruleset: MahjongRuleset,
      descriptor: KyokuDescriptor,
      orderedSeats: Vector[MahjongSeatState]
  ): Option[Vector[PaifuTile]] =
    if descriptor.roundWind != SeatWind.East || descriptor.handNumber != 2 then None
    else
      val livePrefix =
        (0 until 13).toVector.flatMap { tileIndex =>
          orderedSeats.map(seat => showcaseEast2InitialHands(seat.seat)(tileIndex))
        } :+ showcaseEast2EastDraw
      val liveWallSize = 136 - 14
      val liveTailSize = liveWallSize - livePrefix.size

      for
        remainingAfterLivePrefix <- removeTiles(fullWall(ruleset), livePrefix)
        remaining <- removeTiles(remainingAfterLivePrefix, Vector(showcaseEast2DoraIndicator))
        if liveTailSize >= 0 && remaining.size >= liveTailSize + 13
      yield
        val liveWall = livePrefix ++ remaining.take(liveTailSize)
        val deadFill = remaining.drop(liveTailSize)
        val deadSource =
          deadFill.take(4) ++
            Vector(showcaseEast2DoraIndicator) ++
            deadFill.drop(4).take(9)
        liveWall ++ deadSource

  private def dealRound(
      state: MahjongTableState,
      descriptor: KyokuDescriptor,
      seatsForRound: Vector[MahjongSeatState],
      seed: String,
      showcaseMode: Boolean = false
  ): MahjongTableState =
    val orderedSeats = SeatWind.all.flatMap(wind => seatsForRound.find(_.seat == wind))
    val shuffled =
      (if showcaseMode then showcaseWallForRound(state.ruleset, descriptor, orderedSeats) else None)
        .getOrElse(MahjongTileFunctions.shuffledWall(seed, state.ruleset))
    val deadSource = shuffled.takeRight(14)
    val deadWall = deadSource.take(4) ++ deadSource.slice(4, 9)
    val uraDora = deadSource.slice(9, 14)
    val liveWall = shuffled.dropRight(14)
    val playerIds = orderedSeats.map(_.playerId)
    var cursor = 0
    val initialHands = Array.fill(4)(Vector.empty[PaifuTile])
    (0 until 13).foreach { _ =>
      (0 until 4).foreach { seatIndex =>
        initialHands(seatIndex) = initialHands(seatIndex) :+ liveWall(cursor)
        cursor += 1
      }
    }
    val eastDraw = liveWall(cursor)
    cursor += 1
    val nextSequence = state.currentRound.flatMap(_.events.lastOption.map(sequenceNoOf)).getOrElse(0) + 1
    val seats = orderedSeats.zipWithIndex.map { case (seat, index) =>
      seat.copy(
        handTiles = sortTiles(initialHands(index)),
        drawTile = if seat.seat == SeatWind.East then Some(eastDraw) else None,
        melds = Vector.empty,
        river = Vector.empty,
        riichi = false,
        ippatsu = false,
        furiten = false
      )
    }
    val round = MahjongRoundState(
      descriptor = descriptor,
      phase = MahjongRoundPhase.PlayerTurn,
      roundStartSticks = MahjongTableSticks(honba = descriptor.honba, riichi = state.sticks.riichi),
      wall = liveWall.drop(cursor),
      deadWall = deadWall,
      doraIndicators = Vector(deadSource(4)),
      uraDoraIndicators = uraDora,
      initialHands = orderedSeats.zipWithIndex.map { case (seat, index) => seat.playerId -> sortTiles(initialHands(index)) }.toMap,
      turnPlayerId = playerIds.head,
      pendingCall = None,
      events = Vector(
        MahjongEvent.RoundStarted(nextSequence, descriptor),
        MahjongEvent.TileDrawn(nextSequence + 1, playerIds.head, eastDraw)
      ),
      result = None
    )
    state.copy(
      status = MahjongTableStatus.WaitingPlayerAction,
      seats = seats,
      currentRound = Some(round),
      sticks = MahjongTableSticks(honba = descriptor.honba, riichi = state.sticks.riichi)
    )

  private def rotateSeatsForNextDealer(seats: Vector[MahjongSeatState]): Vector[MahjongSeatState] =
    seats.map { seat =>
      seat.copy(seat = previousSeatWind(seat.seat))
    }

  private def shouldFinishTable(
      state: MahjongTableState,
      round: MahjongRoundState,
      dealerContinues: Boolean
  ): Boolean =
    val ruleset = state.ruleset
    val bankruptcyFinished = ruleset.bankruptcyEnd && state.seats.exists(_.points < 0)
    val lengthFinished =
      ruleset.gameLength match
        case MahjongGameLength.OneKyoku => true
        case MahjongGameLength.Tonpu | MahjongGameLength.Hanchan =>
          !dealerContinues &&
            isAtOrBeyondLastScheduledHand(round.descriptor, ruleset.gameLength) &&
            state.seats.exists(_.points >= ruleset.targetPoints)
    bankruptcyFinished || lengthFinished

  private def isAtOrBeyondLastScheduledHand(
      descriptor: KyokuDescriptor,
      gameLength: MahjongGameLength
  ): Boolean =
    descriptor.handNumber >= 4 &&
      roundWindOrder(descriptor.roundWind) >= roundWindOrder(lastScheduledRoundWind(gameLength))

  private def lastScheduledRoundWind(gameLength: MahjongGameLength): SeatWind =
    gameLength match
      case MahjongGameLength.OneKyoku | MahjongGameLength.Tonpu => SeatWind.East
      case MahjongGameLength.Hanchan => SeatWind.South

  private def roundWindOrder(seat: SeatWind): Int =
    SeatWind.all.indexOf(seat)

  private def finalStandings(state: MahjongTableState): Vector[FinalStanding] =
    state.seats
      .sortBy(seat => (-seat.points, seat.seat.ordinal))
      .zipWithIndex
      .map { case (seat, index) =>
        FinalStanding(
          playerId = seat.playerId,
          seat = seat.seat,
          finalPoints = seat.points,
          placement = index + 1
        )
      }

  private def previousSeatWind(seat: SeatWind): SeatWind =
    val index = SeatWind.all.indexOf(seat)
    SeatWind.all((index - 1 + SeatWind.all.size) % SeatWind.all.size)

  private def nextDescriptorAfterDealerPass(descriptor: KyokuDescriptor, outcome: HandOutcome): KyokuDescriptor =
    val nextHand = descriptor.handNumber + 1
    val honba = if outcome == HandOutcome.ExhaustiveDraw then descriptor.honba + 1 else 0
    if nextHand <= 4 then descriptor.copy(handNumber = nextHand, honba = honba)
    else
      val nextRoundWind = nextSeatWind(descriptor.roundWind)
      KyokuDescriptor(nextRoundWind, handNumber = 1, honba = honba)

  private def nextSeatWind(seat: SeatWind): SeatWind =
    SeatWind.all((SeatWind.all.indexOf(seat) + 1) % SeatWind.all.size)

  private def finishedRoundFromState(state: MahjongTableState, round: MahjongRoundState): PaifuRound =
    val timeline = PaifuTimeline(Vector.empty)
    val players = state.seats.map { seat =>
      PaifuRoundPlayer(
        playerId = seat.playerId,
        seat = seat.seat,
        initialHand = PaifuHand(round.initialHands.getOrElse(seat.playerId, seat.handTiles)),
        track = PaifuPlayerTrack(Vector.empty)
      )
    }
    PaifuRound(
      descriptor = round.descriptor,
      players = players,
      timeline = timeline,
      result = round.result.get
    )

  private def leavesTenpaiAfterDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    val source = seat.handTiles ++ seat.drawTile.toVector
    MahjongTileFunctions.removeTiles(source, Vector(discardTile)).exists { remaining =>
      MahjongHandAnalysisFunctions.calculateShanten(remaining, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0
    }

  private def isWinningOwnDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    MahjongHandAnalysisFunctions.isWinning(seat.handTiles :+ discardTile, seat.melds.size, allowSpecialHands = seat.melds.isEmpty)

  private def applyMeldEvent(
      state: MahjongTableState,
      playerId: PlayerId,
      meld: MahjongMeld
  ): MahjongTableState =
    val caller = seatByPlayerId(state, playerId)
    val sourceTiles = caller.handTiles ++ caller.drawTile.toVector
    val tilesToRemove =
      if meld.closed then meld.tiles
      else meld.calledTile match
        case Some(calledTile) => removeOneByIndex(meld.tiles, indexOf(calledTile))
        case None => Vector.empty
    val handAfterMeld = MahjongTileFunctions.removeTiles(sourceTiles, tilesToRemove).getOrElse(caller.handTiles)
    val nextMelds =
      if meld.meldType == MahjongMeldType.AddedKan then
        val ponIndex = caller.melds.indexWhere(existing =>
          existing.meldType == MahjongMeldType.Pon &&
            existing.tiles.headOption.exists(tile =>
              meld.tiles.headOption.exists(upgraded => indexOf(tile) == indexOf(upgraded))
            )
        )
        if ponIndex >= 0 then caller.melds.updated(ponIndex, meld) else caller.melds :+ meld
      else caller.melds :+ meld
    val callerAfterMeld = caller.copy(
      handTiles = sortTiles(handAfterMeld),
      drawTile = None,
      melds = nextMelds,
      ippatsu = false
    )
    val seatsWithCalledDiscard =
      (meld.fromPlayer, meld.calledTile) match
        case (Some(fromPlayer), Some(calledTile)) => markLatestDiscardCalledBy(state.seats, fromPlayer, calledTile, playerId)
        case _ => state.seats
    state.copy(seats = replaceSeat(seatsWithCalledDiscard.map(_.copy(ippatsu = false)), callerAfterMeld))

  private def markLatestDiscardCalledBy(
      seats: Vector[MahjongSeatState],
      discardPlayerId: PlayerId,
      calledTile: PaifuTile,
      calledBy: PlayerId
  ): Vector[MahjongSeatState] =
    seats.map { seat =>
      if seat.playerId != discardPlayerId then seat
      else
        val targetIndex = seat.river.lastIndexWhere(discard =>
          discard.calledBy.isEmpty && indexOf(discard.tile) == indexOf(calledTile)
        )
        if targetIndex < 0 then seat
        else
          seat.copy(river = seat.river.zipWithIndex.map { case (discard, index) =>
            if index == targetIndex then discard.copy(calledBy = Some(calledBy)) else discard
          })
    }

  private def winContext(
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

  private def drawResult(
      state: MahjongTableState,
      round: MahjongRoundState,
      outcome: HandOutcome,
      notes: Vector[String]
  ): AgariResult =
    if outcome == HandOutcome.ExhaustiveDraw && state.ruleset.nagashiMangan then
      nagashiManganResult(state, round, notes).getOrElse(exhaustiveDrawResult(state, outcome, notes))
    else if outcome == HandOutcome.AbortiveDraw then abortiveDrawResult(state, notes)
    else exhaustiveDrawResult(state, outcome, notes)

  private def abortiveDrawResult(state: MahjongTableState, notes: Vector[String]): AgariResult =
    AgariResult(
      outcome = HandOutcome.AbortiveDraw,
      yaku = Vector.empty,
      points = 0,
      scoreChanges = state.seats.map(seat => ScoreChange(seat.playerId, 0)),
      settlement = Some(RoundSettlement(notes = notes))
    )

  private def exhaustiveDrawResult(state: MahjongTableState, outcome: HandOutcome, notes: Vector[String]): AgariResult =
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

  private def nagashiManganResult(
      state: MahjongTableState,
      round: MahjongRoundState,
      notes: Vector[String]
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
        settlement = Some(RoundSettlement(notes = (notes :+ "流局满贯").distinct)),
        wins = wins
      )
    }

  private def isNagashiManganSeat(seat: MahjongSeatState): Boolean =
    seat.melds.isEmpty &&
      seat.river.nonEmpty &&
      seat.river.forall(discard => isYaochu(indexOf(discard.tile)) && discard.calledBy.isEmpty)

  private def manganTsumoScoreChanges(
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

  private def exhaustiveDrawScoreChanges(players: Vector[PlayerId], tenpaiPlayers: Vector[PlayerId]): Vector[ScoreChange] =
    if tenpaiPlayers.isEmpty || tenpaiPlayers.size == players.size then players.map(ScoreChange(_, 0))
    else
      val notenPlayers = players.filterNot(tenpaiPlayers.contains)
      val tenpaiGain = 3000 / tenpaiPlayers.size
      val notenLoss = 3000 / notenPlayers.size
      players.map { player =>
        if tenpaiPlayers.contains(player) then ScoreChange(player, tenpaiGain)
        else ScoreChange(player, -notenLoss)
      }

  private def acceptPendingRiichiDeclaration(state: MahjongTableState, pending: MahjongPendingCallState): MahjongTableState =
    state.seats
      .find(_.playerId == pending.discardPlayerId)
      .flatMap(_.river.find(_.sequenceNo == pending.discardSequenceNo))
      .fold(state)(discard => acceptRiichiDeclarationForDiscard(state, discard))

  private def acceptRiichiDeclarationForDiscard(state: MahjongTableState, discard: MahjongDiscard): MahjongTableState =
    if !discard.riichiDeclared then state
    else
      val declarer = seatByPlayerId(state, discard.playerId)
      val updatedDeclarer = declarer.copy(points = declarer.points - 1000)
      state.copy(
        seats = replaceSeat(state.seats, updatedDeclarer),
        sticks = state.sticks.copy(riichi = state.sticks.riichi + 1)
      )

  private def applyWinSettlementAdjustments(
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

  private def honbaSettlementChanges(
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

  private def mergeSettlement(
      existing: Option[RoundSettlement],
      riichiAward: Int,
      honbaPayment: Int
  ): RoundSettlement =
    val base = existing.getOrElse(RoundSettlement())
    base.copy(
      riichiSticksDelta = riichiAward,
      honbaPayment = honbaPayment
    )

  private def sticksAfterRoundResult(state: MahjongTableState, result: AgariResult): MahjongTableSticks =
    result.outcome match
      case HandOutcome.Ron | HandOutcome.Tsumo => state.sticks.copy(riichi = 0)
      case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw => state.sticks

  private def visibleTenpai(
      seat: MahjongSeatState,
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId],
      revealAllHands: Boolean
  ): Option[Boolean] =
    val canSeeTenpai =
      revealAllHands ||
        viewerPlayerId.contains(seat.playerId) ||
        state.currentRound.flatMap(_.result).exists(_.outcome == HandOutcome.ExhaustiveDraw)
    Option.when(canSeeTenpai)(
      MahjongHandAnalysisFunctions.calculateShanten(seat.handTiles, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0
    )

  private def seatToView(
      seat: MahjongSeatState,
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId],
      revealAllHands: Boolean
  ): MahjongSeatView =
    val visibleHand = viewerPlayerId match
      case _ if revealAllHands || shouldRevealSettlementHand(seat, state) => Some(sortTiles(seat.handTiles ++ seat.drawTile.toVector))
      case Some(viewer) if viewer == seat.playerId => Some(sortTiles(seat.handTiles ++ seat.drawTile.toVector))
      case _ => None
    MahjongSeatView(
      seat = seat.seat,
      playerId = seat.playerId,
      points = seat.points,
      isDealer = seat.seat == SeatWind.East,
      handTiles = visibleHand,
      drawTile = visibleHand.flatMap(_ => seat.drawTile),
      handTileCount = seat.handTiles.size + seat.drawTile.size,
      melds = seat.melds,
      river = seat.river,
      riichi = seat.riichi,
      ippatsu = seat.ippatsu,
      furiten = seat.furiten,
      tenpai = visibleTenpai(seat, state, viewerPlayerId, revealAllHands)
    )

  private def shouldRevealSettlementHand(seat: MahjongSeatState, state: MahjongTableState): Boolean =
    state.currentRound.flatMap(_.result).exists(result => winningPlayerIds(result).contains(seat.playerId))

  private def roundToView(
      round: MahjongRoundState,
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId]
  ): MahjongRoundView =
    val viewerHasPendingCall = viewerPlayerId.exists(playerId =>
      round.pendingCall.exists(_.candidates.exists(_.playerId == playerId))
    )
    MahjongRoundView(
      descriptor = round.descriptor,
      phase = round.phase,
      turnPlayerId = Option.when(round.phase == MahjongRoundPhase.PlayerTurn)(round.turnPlayerId),
      wallTileCount = round.wall.size,
      sticks = state.sticks,
      doraIndicators = round.doraIndicators,
      doraIndicatorVisibleCount = round.doraIndicators.size,
      pendingCall = round.pendingCall.filter(_ => viewerHasPendingCall).map(pending =>
        MahjongPendingCallView(
          discardSequenceNo = pending.discardSequenceNo,
          discardPlayerId = pending.discardPlayerId,
          tile = pending.tile,
          waitingPlayerIds = Vector.empty
        )
      ),
      result = round.result
    )

  private def eventToPublicView(event: MahjongEvent): MahjongPublicEventView =
    event match
      case MahjongEvent.TileDrawn(sequenceNo, playerId, tile) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), PaifuActionType.Draw, tile = Some(tile))
      case MahjongEvent.TileDiscarded(sequenceNo, playerId, tile, _) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), PaifuActionType.Discard, tile = Some(tile))
      case MahjongEvent.MeldCalled(sequenceNo, playerId, meld) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), meldActionType(meld.meldType), tile = meld.calledTile, tiles = meld.tiles)
      case MahjongEvent.KanDeclared(sequenceNo, playerId, meld) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), meldActionType(meld.meldType), tile = meld.calledTile.orElse(meld.tiles.headOption), tiles = meld.tiles)
      case MahjongEvent.RiichiDeclared(sequenceNo, playerId, tile) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), PaifuActionType.Riichi, tile = Some(tile))
      case MahjongEvent.DoraRevealed(sequenceNo, tile) =>
        MahjongPublicEventView(sequenceNo, None, PaifuActionType.DoraReveal, tile = Some(tile))
      case MahjongEvent.WinDeclared(sequenceNo, winner, _, tile) =>
        MahjongPublicEventView(sequenceNo, Some(winner), PaifuActionType.Win, tile = Some(tile))
      case MahjongEvent.RoundFinished(sequenceNo, result) =>
        MahjongPublicEventView(sequenceNo, result.winner, if result.winner.isDefined then PaifuActionType.Win else PaifuActionType.DrawGame, note = Some(result.outcome.toString))
      case MahjongEvent.PlayerPassed(sequenceNo, playerId) =>
        MahjongPublicEventView(sequenceNo, Some(playerId), PaifuActionType.Discard, note = Some("pass"))
      case MahjongEvent.TableStarted(sequenceNo) =>
        MahjongPublicEventView(sequenceNo, None, PaifuActionType.Draw, note = Some("table started"))
      case MahjongEvent.RoundStarted(sequenceNo, descriptor) =>
        MahjongPublicEventView(sequenceNo, None, PaifuActionType.Draw, note = Some(s"${SeatWind.toString(descriptor.roundWind)} ${descriptor.handNumber}"))
      case MahjongEvent.TableFinished(sequenceNo, _) =>
        MahjongPublicEventView(sequenceNo, None, PaifuActionType.DrawGame, note = Some("table finished"))

  private def meldActionType(meldType: MahjongMeldType): PaifuActionType =
    meldType match
      case MahjongMeldType.Chi => PaifuActionType.Chi
      case MahjongMeldType.Pon => PaifuActionType.Pon
      case MahjongMeldType.OpenKan => PaifuActionType.OpenKan
      case MahjongMeldType.ClosedKan => PaifuActionType.ClosedKan
      case MahjongMeldType.AddedKan => PaifuActionType.AddedKan

  private def matchesSubmittedAction(legalAction: MahjongLegalAction, submitted: MahjongSubmittedAction): Boolean =
    legalAction.commandType == submitted.commandType &&
      submitted.tile.forall(tile => legalAction.tile.exists(legalTile => indexOf(legalTile) == indexOf(tile))) &&
      submitted.targetSequenceNo.forall(sequenceNo => legalAction.targetSequenceNo.contains(sequenceNo)) &&
      (submitted.tiles.isEmpty || tileSignatures(submitted.tiles) == tileSignatures(legalAction.tiles))

  private def tileSignatures(tiles: Vector[PaifuTile]): Vector[(Int, Boolean)] =
    tiles.map(tile => indexOf(tile) -> isRed(tile)).sortBy { case (index, red) => (index, red) }

  private def defaultMeldTiles(commandType: MahjongCommandType, tile: PaifuTile): Vector[PaifuTile] =
    val index = indexOf(tile)
    commandType match
      case MahjongCommandType.Pon => Vector.fill(3)(tileOf(index))
      case MahjongCommandType.OpenKan => Vector.fill(4)(tileOf(index))
      case MahjongCommandType.Chi => Vector(tileOf(index), tileOf(index + 1), tileOf(index + 2))
      case _ => Vector(tile)

  private def removeOneByIndex(tiles: Vector[PaifuTile], tileIndex: Int): Vector[PaifuTile] =
    val position = tiles.indexWhere(tile => indexOf(tile) == tileIndex)
    if position < 0 then tiles else tiles.patch(position, Nil, 1)

  private def defaultTableSeats(tableId: TableId, ruleset: MahjongRuleset): Vector[TableSeat] =
    SeatWind.all.map { seat =>
      TableSeat(
        seat = seat,
        playerId = PlayerId(tableId.value + "-" + SeatWind.toString(seat).toLowerCase),
        initialPoints = ruleset.initialPoints
      )
    }

  private def requireRound(state: MahjongTableState): MahjongRoundState =
    state.currentRound.getOrElse(throw IllegalArgumentException(s"Mahjong table ${state.tableId.value} has no active round"))

  private def seatByPlayerId(state: MahjongTableState, playerId: PlayerId): MahjongSeatState =
    state.seats.find(_.playerId == playerId).getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} is not seated at table ${state.tableId.value}"))

  private def replaceSeat(seats: Vector[MahjongSeatState], updatedSeat: MahjongSeatState): Vector[MahjongSeatState] =
    seats.map(seat => if seat.playerId == updatedSeat.playerId then updatedSeat else seat)

  private def nextSeatId(state: MahjongTableState, playerId: PlayerId): PlayerId =
    val seat = seatByPlayerId(state, playerId).seat
    val nextSeat = SeatWind.all((SeatWind.all.indexOf(seat) + 1) % SeatWind.all.size)
    state.seats.find(_.seat == nextSeat).map(_.playerId).getOrElse(playerId)

  private def nextSequenceNo(round: MahjongRoundState): Int =
    round.events.lastOption.map(sequenceNoOf).getOrElse(0) + 1

  private def sequenceNoOf(event: MahjongEvent): Int =
    event match
      case MahjongEvent.TableStarted(sequenceNo) => sequenceNo
      case MahjongEvent.RoundStarted(sequenceNo, _) => sequenceNo
      case MahjongEvent.TileDrawn(sequenceNo, _, _) => sequenceNo
      case MahjongEvent.TileDiscarded(sequenceNo, _, _, _) => sequenceNo
      case MahjongEvent.MeldCalled(sequenceNo, _, _) => sequenceNo
      case MahjongEvent.RiichiDeclared(sequenceNo, _, _) => sequenceNo
      case MahjongEvent.KanDeclared(sequenceNo, _, _) => sequenceNo
      case MahjongEvent.DoraRevealed(sequenceNo, _) => sequenceNo
      case MahjongEvent.WinDeclared(sequenceNo, _, _, _) => sequenceNo
      case MahjongEvent.PlayerPassed(sequenceNo, _) => sequenceNo
      case MahjongEvent.RoundFinished(sequenceNo, _) => sequenceNo
      case MahjongEvent.TableFinished(sequenceNo, _) => sequenceNo

  private def markDiscardCalledBy(
      seats: Vector[MahjongSeatState],
      discardPlayerId: PlayerId,
      discardSequenceNo: Int,
      calledBy: PlayerId
  ): Vector[MahjongSeatState] =
    seats.map { seat =>
      if seat.playerId != discardPlayerId then seat
      else
        seat.copy(river = seat.river.map { discard =>
          if discard.sequenceNo == discardSequenceNo then discard.copy(calledBy = Some(calledBy)) else discard
        })
    }

  private def applyScoreChanges(seats: Vector[MahjongSeatState], changes: Vector[ScoreChange]): Vector[MahjongSeatState] =
    val deltaByPlayer = changes.groupMapReduce(_.playerId)(_.delta)(_ + _)
    seats.map(seat => seat.copy(points = seat.points + deltaByPlayer.getOrElse(seat.playerId, 0)))

  private def aggregateScoreChanges(players: Vector[PlayerId], changes: Vector[ScoreChange]): Vector[ScoreChange] =
    val deltaByPlayer = changes.groupMapReduce(_.playerId)(_.delta)(_ + _)
    players.map(playerId => ScoreChange(playerId, deltaByPlayer.getOrElse(playerId, 0)))

  private def singleWinFromResult(result: AgariResult): Option[AgariWinResult] =
    result.winner.map { winner =>
      AgariWinResult(
        winner = winner,
        target = result.target,
        han = result.han,
        fu = result.fu,
        yaku = result.yaku,
        points = result.points,
        doraIndicators = result.doraIndicators,
        uraDoraIndicators = result.uraDoraIndicators,
        uraDoraVisible = result.uraDoraVisible
      )
    }

  private def winningPlayerIds(result: AgariResult): Vector[PlayerId] =
    val winIds = result.wins.map(_.winner)
    if winIds.nonEmpty then winIds else result.winner.toVector

  private def ronWinnerIdsBySeatOrder(
      state: MahjongTableState,
      pending: MahjongPendingCallState
  ): Vector[PlayerId] =
    val ordered = pending.acceptedRonPlayerIds
      .distinct
      .sortBy(playerId => seatDistanceFromDiscarder(state, pending.discardPlayerId, playerId))
    if state.ruleset.doubleRon then ordered else ordered.take(1)

  private def seatDistanceFromDiscarder(
      state: MahjongTableState,
      discardPlayerId: PlayerId,
      targetPlayerId: PlayerId
  ): Int =
    val discardSeat = seatByPlayerId(state, discardPlayerId).seat
    val targetSeat = seatByPlayerId(state, targetPlayerId).seat
    val discardIndex = SeatWind.all.indexOf(discardSeat)
    val targetIndex = SeatWind.all.indexOf(targetSeat)
    (targetIndex - discardIndex + SeatWind.all.size) % SeatWind.all.size
