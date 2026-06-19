package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRoundPhase, MahjongTableStatus}
import riichinexus.microservices.tournament.objects.paifu.{HandOutcome, RoundSettlementNote}

import MahjongGameStateSupport.{applyScoreChanges, nextSequenceNo, replaceSeat, requireRound, seatByPlayerId}
import MahjongWinSettlementFunctions.{drawResult, finishRoundWithAbortiveDraw}

/** MahjongDrawActionFunctions 提供麻将摸牌动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongDrawActionFunctions:
  private[mahjongcore] def abortiveDraw(state: MahjongTableState, note: Option[RoundSettlementNote]): (MahjongTableState, Option[MahjongEvent]) =
    val round = requireRound(state)
    finishRoundWithAbortiveDraw(state, round, note.toVector, acceptedEvent = None)

  private[mahjongcore] def drawForNextPlayer(state: MahjongTableState, round: MahjongRoundState, nextPlayerId: PlayerId): MahjongTableState =
    if round.wall.isEmpty then
      val result = drawResult(state, round, HandOutcome.ExhaustiveDraw, Vector(RoundSettlementNote.ExhaustiveDraw))
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
