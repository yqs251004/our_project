package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongCallCandidate, MahjongCallResponse, MahjongPendingCallState, MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{countsOf, indexOf, isRed, isSuited, sortTiles, tileOf}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongMeld, MahjongMeldType, MahjongRoundPhase, MahjongTableStatus}
import riichinexus.microservices.tournament.objects.paifumanagement.{PaifuTile, RoundSettlementNote}

import MahjongGameStateSupport.{defaultMeldTiles, markDiscardCalledBy, nextSeatId, nextSequenceNo, removeOneByIndex, replaceSeat, requireRound, seatByPlayerId, seatDistanceFromDiscarder}
import MahjongWinSettlementFunctions.{acceptPendingRiichiDeclaration, finishRoundWithAbortiveDraw, finishRoundWithRonWinners, winContext}

/** MahjongCallActionFunctions 提供麻将鸣牌动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongCallActionFunctions:
  private[mahjongcore] def recordCallResponse(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to resolve"))
    val response = MahjongCallResponse(playerId, legalAction)
    val responseEvent = Option.when(legalAction.commandType == MahjongCommandType.Pass)(
      MahjongEvent.PlayerPassed(nextSequenceNo(round), playerId)
    )
    val updatedPending = pending.copy(
      responses = (pending.responses.filterNot(_.playerId == playerId) :+ response)
    )
    val updatedRound = round.copy(
      pendingCall = Some(updatedPending),
      events = round.events ++ responseEvent.toVector
    )
    val updatedState = state.copy(currentRound = Some(updatedRound), status = MahjongTableStatus.WaitingCallDecision)

    if !canResolveCallResponses(state, updatedPending) then
      updatedState -> None
    else
      resolveCallResponses(updatedState, updatedRound, updatedPending)

  private[mahjongcore] def resolveCallResponses(
      state: MahjongTableState,
      round: MahjongRoundState,
      pending: MahjongPendingCallState
  ): (MahjongTableState, Option[MahjongEvent]) =
    val selectedResponses = selectedCallResponses(state, pending)
    val ronResponses = selectedResponses.filter(_.action.commandType == MahjongCommandType.Ron)

    if ronResponses.nonEmpty then
      ronResponses.foreach(response =>
        MahjongYakuAnalysisFunctions.analyzeWin(winContext(state, response.playerId, Some(pending.discardPlayerId), pending.tile))
          .getOrElse(throw IllegalArgumentException(s"Submitted ron is not a winning hand for ${response.playerId.value}"))
      )
      val acceptedRonPlayerIds = ronResponses.map(_.playerId).distinct.sortBy(playerId => seatDistanceFromDiscarder(state, pending.discardPlayerId, playerId))
      val updatedPending = pending.copy(
        candidates = Vector.empty,
        acceptedRonPlayerIds = acceptedRonPlayerIds
      )
      if state.ruleset.tripleRonAbortiveDraw && acceptedRonPlayerIds.size >= 3 then
        finishRoundWithAbortiveDraw(
          state,
          round.copy(pendingCall = Some(updatedPending)),
          Vector(RoundSettlementNote.TripleRonAbortiveDraw),
          acceptedEvent = None
        )
      else finishRoundWithRonWinners(state, round.copy(pendingCall = Some(updatedPending)), updatedPending, acceptedEvent = None)
    else
      selectedResponses.find(response => isMeldCommand(response.action.commandType)) match
        case Some(response) => callMeld(state, response.playerId, response.action)
        case None =>
          val stateAfterRiichiFuriten =
            pending.responses.foldLeft(state) { (current, response) =>
              if response.action.commandType == MahjongCommandType.Pass then markRiichiMissedRonFuriten(current, pending, response.playerId)
              else current
            }
          val clearedRound = round.copy(pendingCall = None)
          val stateWithAcceptedRiichi = acceptPendingRiichiDeclaration(stateAfterRiichiFuriten, pending)
          MahjongDrawActionFunctions.drawForNextPlayer(stateWithAcceptedRiichi, clearedRound, nextSeatId(state, pending.discardPlayerId)) -> None

  private[mahjongcore] def canResolveCallResponses(
      state: MahjongTableState,
      pending: MahjongPendingCallState
  ): Boolean =
    val responsesByPlayer = pending.responses.map(response => response.playerId -> response).toMap
    val highestUnresolvedPriorities = pending.candidates.flatMap { candidate =>
      responsesByPlayer.get(candidate.playerId) match
        case Some(response) => Vector(response.action.priority)
        case None => legalActionsForCandidate(state, pending, candidate).map(_.priority).filter(_ > 0)
    }
    highestUnresolvedPriorities.maxOption match
      case None => pending.responses.map(_.playerId).distinct.size == pending.candidates.map(_.playerId).distinct.size
      case Some(highestPriority) =>
        val topCandidateCount = pending.candidates.count { candidate =>
          responsesByPlayer.get(candidate.playerId) match
            case Some(response) => response.action.priority == highestPriority
            case None => legalActionsForCandidate(state, pending, candidate).exists(_.priority == highestPriority)
        }
        val topResponseCount = pending.responses.count(_.action.priority == highestPriority)
        topResponseCount > 0 && topResponseCount == topCandidateCount

  private[mahjongcore] def callMeld(
      state: MahjongTableState,
      playerId: PlayerId,
      legalAction: MahjongLegalAction
  ): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    val pending = round.pendingCall.getOrElse(throw IllegalArgumentException("No pending call to resolve"))
    val stateWithAcceptedRiichi = acceptPendingRiichiDeclaration(state, pending)
    val caller = seatByPlayerId(stateWithAcceptedRiichi, playerId)
    val discardTile = pending.tile
    val declaredMeldTiles = legalAction.tiles.nonEmpty match
      case true => legalAction.tiles.map(MahjongTileFunctions.normalize)
      case false => defaultMeldTiles(legalAction.commandType, discardTile)
    val handTilesToRemove = removeOneByIndex(declaredMeldTiles, indexOf(discardTile))
    val (handAfterCall, removedHandTiles) = MahjongTileFunctions.removeTilesWithRemoved(caller.handTiles, handTilesToRemove)
      .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} cannot call ${legalAction.commandType}"))
    val meldTiles = discardTile +: removedHandTiles
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
      MahjongKanActionFunctions.drawReplacementAfterKan(nextState, baseRound, callerAfterCall.playerId, event)
    else
      nextState.copy(currentRound = Some(baseRound), status = MahjongTableStatus.WaitingPlayerAction) -> Some(event)

  private[mahjongcore] def legalActionsForCandidate(
      state: MahjongTableState,
      pending: MahjongPendingCallState,
      candidate: MahjongCallCandidate
  ): Vector[MahjongLegalAction] =
    if !state.ruleset.doubleRon && hasRonAction(candidate) then
      closestRonCandidate(state, pending).filter(_ == candidate.playerId).fold(Vector.empty[MahjongLegalAction])(_ => candidate.legalActions)
    else if !state.ruleset.doubleRon && pending.candidates.exists(hasRonAction) then Vector.empty
    else candidate.legalActions

  private[mahjongcore] def selectedCallResponses(
      state: MahjongTableState,
      pending: MahjongPendingCallState
  ): Vector[MahjongCallResponse] =
    val nonPassResponses = pending.responses.filterNot(_.action.commandType == MahjongCommandType.Pass)
    if nonPassResponses.isEmpty then Vector.empty
    else
      val highestPriority = nonPassResponses.map(_.action.priority).max
      val highestResponses = nonPassResponses.filter(_.action.priority == highestPriority)
      if highestPriority == 100 && state.ruleset.doubleRon then
        highestResponses
      else
        highestResponses
          .sortBy(response => seatDistanceFromDiscarder(state, pending.discardPlayerId, response.playerId))
          .take(1)

  private[mahjongcore] def isMeldCommand(commandType: MahjongCommandType): Boolean =
    commandType == MahjongCommandType.Chi ||
      commandType == MahjongCommandType.Pon ||
      commandType == MahjongCommandType.OpenKan

  private[mahjongcore] def hasRonAction(candidate: MahjongCallCandidate): Boolean =
    candidate.legalActions.exists(_.commandType == MahjongCommandType.Ron)

  private[mahjongcore] def closestRonCandidate(state: MahjongTableState, pending: MahjongPendingCallState): Option[PlayerId] =
    pending.candidates
      .filter(hasRonAction)
      .sortBy(candidate => seatDistanceFromDiscarder(state, pending.discardPlayerId, candidate.playerId))
      .headOption
      .map(_.playerId)

  private[mahjongcore] def ronLegalAction(
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

  private[mahjongcore] def markRiichiMissedRonFuriten(
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

  private[mahjongcore] def ponLegalAction(seat: MahjongSeatState, discard: MahjongDiscard): Option[MahjongLegalAction] =
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

  private[mahjongcore] def openKanLegalAction(seat: MahjongSeatState, discard: MahjongDiscard): Option[MahjongLegalAction] =
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

  private[mahjongcore] def chiLegalActions(seat: MahjongSeatState, discard: MahjongDiscard): Vector[MahjongLegalAction] =
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

  private[mahjongcore] def tileChoiceCombinations(
      neededIndices: Vector[Int],
      handTiles: Vector[PaifuTile]
  ): Vector[Vector[PaifuTile]] =
    neededIndices.foldLeft(Vector(Vector.empty[PaifuTile])) { (combinations, tileIndex) =>
      val choices = handTileChoices(tileIndex, handTiles)
      combinations.flatMap(combination => choices.map(choice => combination :+ choice))
    }

  private[mahjongcore] def handTileChoices(tileIndex: Int, handTiles: Vector[PaifuTile]): Vector[PaifuTile] =
    handTiles
      .map(MahjongTileFunctions.normalize)
      .filter(tile => indexOf(tile) == tileIndex)
      .distinctBy(tile => isRed(tile))
