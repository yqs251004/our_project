package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongCallCandidate, MahjongPendingCallState, MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.domain.paifu.functions.PaifuTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.sortTiles
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongRoundPhase, MahjongTableStatus}
import riichinexus.microservices.tournament.objects.paifu.PaifuTile

import MahjongGameStateSupport.{nextSeatId, nextSequenceNo, replaceSeat, requireRound, seatByPlayerId}
import MahjongWinSettlementFunctions.{acceptRiichiDeclarationForDiscard}

/** MahjongDiscardActionFunctions 提供麻将弃牌动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongDiscardActionFunctions:
  private[mahjongcore] def discard(
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
          .getOrElse(throw IllegalArgumentException(s"Player ${playerId.value} does not have tile ${PaifuTileFunctions.toString(tile)}"))
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
        nextState -> riichiEvent.orElse(Some(discardEvent))
      case None =>
        val stateWithAcceptedRiichi = acceptRiichiDeclarationForDiscard(state.copy(seats = seats), discardView)
        MahjongDrawActionFunctions.drawForNextPlayer(stateWithAcceptedRiichi, roundWithDiscard, nextSeatId(state, playerId)) -> riichiEvent.orElse(Some(discardEvent))

  private[mahjongcore] def buildPendingCall(
      state: MahjongTableState,
      round: MahjongRoundState,
      discard: MahjongDiscard
  ): Option[MahjongPendingCallState] =
    val candidates = state.seats.filterNot(_.playerId == discard.playerId).flatMap { seat =>
      val ron = MahjongCallActionFunctions.ronLegalAction(state, round, seat, discard)
      val actions =
        if seat.riichi then ron.toVector
        else
          val pon = MahjongCallActionFunctions.ponLegalAction(seat, discard)
          val kan = MahjongCallActionFunctions.openKanLegalAction(seat, discard)
          val chi = if seat.playerId == nextSeatId(state, discard.playerId) then MahjongCallActionFunctions.chiLegalActions(seat, discard) else Vector.empty
          ron.toVector ++ kan.toVector ++ pon.toVector ++ chi
      Option.when(actions.nonEmpty)(MahjongCallCandidate(seat.playerId, actions))
    }
    Option.when(candidates.nonEmpty)(
      MahjongPendingCallState(discard.sequenceNo, discard.playerId, discard.tile, candidates)
    )

  private[mahjongcore] def sameTile(left: PaifuTile, right: PaifuTile): Boolean =
    MahjongTileFunctions.normalize(left) == MahjongTileFunctions.normalize(right)

  private[mahjongcore] def isWinningOwnDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    MahjongHandAnalysisFunctions.isWinning(seat.handTiles :+ discardTile, seat.melds.size, allowSpecialHands = seat.melds.isEmpty)
