package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongPendingCallState, MahjongRoundState, MahjongSeatState, MahjongSubmittedAction, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{indexOf, isRed, sortTiles, tileOf}
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongMeldType, MahjongRuleset}
import riichinexus.microservices.tournament.objects.paifumanagement.{AgariResult, AgariWinResult, PaifuTile, ScoreChange}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId, TableSeat}

/** MahjongGameStateSupport 提供麻将游戏状态支撑 相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongGameStateSupport:
  private[mahjongcore] def applyMeldEvent(
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

  private[mahjongcore] def markLatestDiscardCalledBy(
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
  private[mahjongcore] def matchesSubmittedAction(legalAction: MahjongLegalAction, submitted: MahjongSubmittedAction): Boolean =
    legalAction.commandType == submitted.commandType &&
      submitted.tile.forall(tile => legalAction.tile.exists(legalTile => indexOf(legalTile) == indexOf(tile))) &&
      submitted.targetSequenceNo.forall(sequenceNo => legalAction.targetSequenceNo.contains(sequenceNo)) &&
      (submitted.tiles.isEmpty || tileSignatures(submitted.tiles) == tileSignatures(legalAction.tiles))

  private[mahjongcore] def tileSignatures(tiles: Vector[PaifuTile]): Vector[(Int, Boolean)] =
    tiles.map(tile => indexOf(tile) -> isRed(tile)).sortBy { case (index, red) => (index, red) }

  private[mahjongcore] def defaultMeldTiles(commandType: MahjongCommandType, tile: PaifuTile): Vector[PaifuTile] =
    val index = indexOf(tile)
    commandType match
      case MahjongCommandType.Pon => Vector.fill(3)(tileOf(index))
      case MahjongCommandType.OpenKan => Vector.fill(4)(tileOf(index))
      case MahjongCommandType.Chi => Vector(tileOf(index), tileOf(index + 1), tileOf(index + 2))
      case _ => Vector(tile)

  private[mahjongcore] def removeOneByIndex(tiles: Vector[PaifuTile], tileIndex: Int): Vector[PaifuTile] =
    val position = tiles.indexWhere(tile => indexOf(tile) == tileIndex)
    if position < 0 then tiles else tiles.patch(position, Nil, 1)

  private[mahjongcore] def defaultTableSeats(tableId: TableId, ruleset: MahjongRuleset): Vector[TableSeat] =
    SeatWind.all.map { seat =>
      TableSeat(
        seat = seat,
        playerId = PlayerId(tableId.value + "-" + SeatWind.toString(seat).toLowerCase),
        initialPoints = ruleset.initialPoints
      )
    }

  private[mahjongcore] def requireRound(state: MahjongTableState): MahjongRoundState =
    state.currentRound.getOrElse(throw IllegalArgumentException(s"Mahjong table ${state.tableId.value} has no active round"))

  private[mahjongcore] def seatByPlayerId(state: MahjongTableState, playerId: PlayerId): MahjongSeatState =
    state.seats.find(_.playerId == playerId).getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} is not seated at table ${state.tableId.value}"))

  private[mahjongcore] def replaceSeat(seats: Vector[MahjongSeatState], updatedSeat: MahjongSeatState): Vector[MahjongSeatState] =
    seats.map(seat => if seat.playerId == updatedSeat.playerId then updatedSeat else seat)

  private[mahjongcore] def nextSeatId(state: MahjongTableState, playerId: PlayerId): PlayerId =
    val seat = seatByPlayerId(state, playerId).seat
    val nextSeat = SeatWind.all((SeatWind.all.indexOf(seat) + 1) % SeatWind.all.size)
    state.seats.find(_.seat == nextSeat).map(_.playerId).getOrElse(playerId)

  private[mahjongcore] def nextSequenceNo(round: MahjongRoundState): Int =
    round.events.lastOption.map(sequenceNoOf).getOrElse(0) + 1

  private[mahjongcore] def sequenceNoOf(event: MahjongEvent): Int =
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

  private[mahjongcore] def markDiscardCalledBy(
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

  private[mahjongcore] def applyScoreChanges(seats: Vector[MahjongSeatState], changes: Vector[ScoreChange]): Vector[MahjongSeatState] =
    val deltaByPlayer = changes.groupMapReduce(_.playerId)(_.delta)(_ + _)
    seats.map(seat => seat.copy(points = seat.points + deltaByPlayer.getOrElse(seat.playerId, 0)))

  private[mahjongcore] def aggregateScoreChanges(players: Vector[PlayerId], changes: Vector[ScoreChange]): Vector[ScoreChange] =
    val deltaByPlayer = changes.groupMapReduce(_.playerId)(_.delta)(_ + _)
    players.map(playerId => ScoreChange(playerId, deltaByPlayer.getOrElse(playerId, 0)))

  private[mahjongcore] def singleWinFromResult(result: AgariResult): Option[AgariWinResult] =
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

  private[mahjongcore] def winningPlayerIds(result: AgariResult): Vector[PlayerId] =
    val winIds = result.wins.map(_.winner)
    if winIds.nonEmpty then winIds else result.winner.toVector

  private[mahjongcore] def ronWinnerIdsBySeatOrder(
      state: MahjongTableState,
      pending: MahjongPendingCallState
  ): Vector[PlayerId] =
    val ordered = pending.acceptedRonPlayerIds
      .distinct
      .sortBy(playerId => seatDistanceFromDiscarder(state, pending.discardPlayerId, playerId))
    if state.ruleset.doubleRon then ordered else ordered.take(1)

  private[mahjongcore] def seatDistanceFromDiscarder(
      state: MahjongTableState,
      discardPlayerId: PlayerId,
      targetPlayerId: PlayerId
  ): Int =
    val discardSeat = seatByPlayerId(state, discardPlayerId).seat
    val targetSeat = seatByPlayerId(state, targetPlayerId).seat
    val discardIndex = SeatWind.all.indexOf(discardSeat)
    val targetIndex = SeatWind.all.indexOf(targetSeat)
    (targetIndex - discardIndex + SeatWind.all.size) % SeatWind.all.size
