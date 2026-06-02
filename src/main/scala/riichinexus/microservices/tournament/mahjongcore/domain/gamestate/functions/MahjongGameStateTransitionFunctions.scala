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
        points = tableSeat.initialPoints,
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
      includeLegalActions: Boolean
  ): MahjongTableView =
    val legalActions =
      if includeLegalActions then
        viewerPlayerId match
          case Some(playerId) => legalActionsForPlayer(state, playerId)
          case None => state.seats.flatMap(seat => legalActionsForPlayer(state, seat.playerId))
      else Vector.empty

    MahjongTableView(
      tableId = state.tableId,
      status = state.status,
      ruleset = state.ruleset,
      seats = state.seats.map(seatToView(_, state, viewerPlayerId)),
      currentRound = state.currentRound.map(roundToView(_, state)),
      legalActions = legalActions,
      finishedRoundCount = state.finishedRounds.size,
      lastEventSequenceNo = state.currentRound.flatMap(_.events.lastOption.map(sequenceNoOf)).getOrElse(0),
      version = state.version
    )

  def legalActionsForPlayer(state: MahjongTableState, playerId: PlayerId): Vector[MahjongLegalAction] =
    state.currentRound match
      case None => Vector.empty
      case Some(round) =>
        round.pendingCall.flatMap(_.candidates.find(_.playerId == playerId)) match
          case Some(candidate) =>
            candidate.legalActions :+ MahjongLegalAction(
              commandType = MahjongCommandType.Pass,
              tile = Some(round.pendingCall.get.tile),
              fromPlayerId = Some(round.pendingCall.get.discardPlayerId),
              targetSequenceNo = Some(round.pendingCall.get.discardSequenceNo),
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
    val discardTiles = (seat.handTiles ++ seat.drawTile.toVector).map(MahjongTileFunctions.normalize).distinctBy(tile => (indexOf(tile), isRed(tile)))
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
    val fromDraw = seat.drawTile.exists(draw => indexOf(draw) == indexOf(normalizedTile))
    val updatedSeatWithoutDiscard =
      if fromDraw then seat.copy(drawTile = None)
      else
        val updatedHand = MahjongTileFunctions.removeTiles(seat.handTiles, Vector(normalizedTile))
          .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} does not have tile ${tile.value}"))
        seat.copy(handTiles = sortTiles(updatedHand), drawTile = None)

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
        drawForNextPlayer(state.copy(seats = seats), roundWithDiscard, nextSeatId(state, playerId)) -> Some(discardEvent)

  private def passPendingCall(state: MahjongTableState, playerId: PlayerId): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to pass"))
    val event = MahjongEvent.PlayerPassed(nextSequenceNo(round), playerId)
    val remaining = pending.candidates.filterNot(_.playerId == playerId)
    if remaining.nonEmpty then
      val nextRound = round.copy(
        pendingCall = Some(pending.copy(candidates = remaining)),
        events = round.events :+ event
      )
      state.copy(currentRound = Some(nextRound), status = MahjongTableStatus.WaitingCallDecision) -> Some(event)
    else
      val clearedRound = round.copy(pendingCall = None, events = round.events :+ event)
      drawForNextPlayer(state, clearedRound, nextSeatId(state, pending.discardPlayerId)) -> Some(event)

  private def callMeld(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to resolve"))
    val caller = seatByPlayerId(state, playerId)
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
    val seatsAfterCall = replaceSeat(markDiscardCalledBy(state.seats, pending.discardPlayerId, pending.discardSequenceNo, playerId), callerAfterCall).map(_.copy(ippatsu = false))
    val baseRound = round.copy(
      pendingCall = None,
      turnPlayerId = playerId,
      phase = MahjongRoundPhase.PlayerTurn,
      events = round.events :+ event
    )
    val nextState = state.copy(seats = seatsAfterCall)
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
    val nextState = state.copy(seats = replaceSeat(state.seats, seatAfterKan))
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
    val nextState = state.copy(seats = replaceSeat(state.seats, seatAfterKan))
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
    val result = MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, playerId, Some(pending.discardPlayerId), pending.tile))
      .getOrElse(throw IllegalArgumentException("Submitted ron is not a winning hand"))
    finishRoundWithWin(state, round, playerId, target = Some(pending.discardPlayerId), pending.tile, result)

  private def finishRoundWithWin(
      state: MahjongTableState,
      round: MahjongRoundState,
      winner: PlayerId,
      target: Option[PlayerId],
      winningTile: PaifuTile,
      result: AgariResult
  ): (MahjongTableState, Option[MahjongEvent]) =
    val winEvent = MahjongEvent.WinDeclared(nextSequenceNo(round), winner, target, winningTile)
    val finishEvent = MahjongEvent.RoundFinished(nextSequenceNo(round) + 1, result)
    val seatsAfterScore = applyScoreChanges(state.seats, result.scoreChanges)
    val finishedRound = round.copy(
      phase = MahjongRoundPhase.Finished,
      pendingCall = None,
      events = round.events :+ winEvent :+ finishEvent,
      result = Some(result)
    )
    state.copy(
      seats = seatsAfterScore,
      currentRound = Some(finishedRound),
      status = MahjongTableStatus.RoundEnded
    ) -> Some(winEvent)

  private def abortiveDraw(state: MahjongTableState, note: Option[String]): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val result = drawResult(state, HandOutcome.AbortiveDraw, note.toVector)
    val event = MahjongEvent.RoundFinished(nextSequenceNo(round), result)
    state.copy(
      currentRound = Some(round.copy(phase = MahjongRoundPhase.Finished, events = round.events :+ event, result = Some(result))),
      status = MahjongTableStatus.RoundEnded
    ) -> Some(event)

  private def drawForNextPlayer(state: MahjongTableState, round: MahjongRoundState, nextPlayerId: PlayerId): MahjongTableState =
    if round.wall.isEmpty then
      val result = drawResult(state, HandOutcome.ExhaustiveDraw, Vector("荒牌流局"))
      val event = MahjongEvent.RoundFinished(nextSequenceNo(round), result)
      state.copy(
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
      val pon = ponLegalAction(seat, discard)
      val kan = openKanLegalAction(seat, discard)
      val chi = if seat.playerId == nextSeatId(state, discard.playerId) then chiLegalActions(seat, discard) else Vector.empty
      val actions = ron.toVector ++ kan.toVector ++ pon.toVector ++ chi
      Option.when(actions.nonEmpty)(MahjongCallCandidate(seat.playerId, actions))
    }
    Option.when(candidates.nonEmpty)(
      MahjongPendingCallState(discard.sequenceNo, discard.playerId, discard.tile, candidates)
    )

  private def ronLegalAction(
      state: MahjongTableState,
      round: MahjongRoundState,
      seat: MahjongSeatState,
      discard: MahjongDiscard
  ): Option[MahjongLegalAction] =
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
          Some(
            MahjongLegalAction(
              MahjongCommandType.Chi,
              tile = Some(discard.tile),
              tiles = Vector(start, start + 1, start + 2).map(tileOf(_)),
              fromPlayerId = Some(discard.playerId),
              targetSequenceNo = Some(discard.sequenceNo),
              priority = 40
            )
          )
        else None
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

  private def leavesTenpaiAfterDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    val source = seat.handTiles ++ seat.drawTile.toVector
    MahjongTileFunctions.removeTiles(source, Vector(discardTile)).exists { remaining =>
      MahjongHandAnalysisFunctions.calculateShanten(remaining, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0
    }

  private def isWinningOwnDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    MahjongHandAnalysisFunctions.isWinning(seat.handTiles :+ discardTile, seat.melds.size, allowSpecialHands = seat.melds.isEmpty)

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
      tenhou = round.events.count {
        case MahjongEvent.TileDrawn(_, _, _) => true
        case _ => false
      } == 1 && target.isEmpty,
      ruleset = state.ruleset
    )

  private def drawResult(state: MahjongTableState, outcome: HandOutcome, notes: Vector[String]): AgariResult =
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

  private def seatToView(
      seat: MahjongSeatState,
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId]
  ): MahjongSeatView =
    val visibleHand = viewerPlayerId match
      case None => Some(sortTiles(seat.handTiles ++ seat.drawTile.toVector))
      case Some(viewer) if viewer == seat.playerId => Some(sortTiles(seat.handTiles ++ seat.drawTile.toVector))
      case _ => None
    MahjongSeatView(
      seat = seat.seat,
      playerId = seat.playerId,
      points = seat.points,
      isDealer = seat.seat == SeatWind.East,
      handTiles = visibleHand,
      handTileCount = seat.handTiles.size + seat.drawTile.size,
      melds = seat.melds,
      river = seat.river,
      riichi = seat.riichi,
      ippatsu = seat.ippatsu,
      furiten = seat.furiten,
      tenpai = Some(MahjongHandAnalysisFunctions.calculateShanten(seat.handTiles, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0)
    )

  private def roundToView(round: MahjongRoundState, state: MahjongTableState): MahjongRoundView =
    MahjongRoundView(
      descriptor = round.descriptor,
      phase = round.phase,
      turnPlayerId = Option.when(round.phase == MahjongRoundPhase.PlayerTurn)(round.turnPlayerId),
      wallTileCount = round.wall.size,
      sticks = state.sticks,
      doraIndicators = round.doraIndicators,
      doraIndicatorVisibleCount = round.doraIndicators.size,
      pendingCall = round.pendingCall.map(pending =>
        MahjongPendingCallView(
          discardSequenceNo = pending.discardSequenceNo,
          discardPlayerId = pending.discardPlayerId,
          tile = pending.tile,
          waitingPlayerIds = pending.candidates.map(_.playerId)
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
      (submitted.tiles.isEmpty || submitted.tiles.map(indexOf).sorted == legalAction.tiles.map(indexOf).sorted)

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
    val deltaByPlayer = changes.map(change => change.playerId -> change.delta).toMap
    seats.map(seat => seat.copy(points = seat.points + deltaByPlayer.getOrElse(seat.playerId, 0)))
