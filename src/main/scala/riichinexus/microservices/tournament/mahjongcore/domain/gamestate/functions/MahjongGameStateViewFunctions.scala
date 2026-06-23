package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{sortTiles}
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongPublicEventView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeldType, MahjongPendingCallView, MahjongRoundPhase, MahjongRoundView, MahjongSeatView, MahjongTableView}
import riichinexus.microservices.tournament.objects.paifu.{HandOutcome, PaifuActionType}
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import MahjongGameStateSupport.{sequenceNoOf, winningPlayerIds}

/** MahjongGameStateViewFunctions 提供麻将游戏状态视图相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongGameStateViewFunctions:
  def toView(
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId],
      includeLegalActions: Boolean,
      revealAllHands: Boolean = false
  ): MahjongTableView =
    val legalActions =
      if includeLegalActions then
        viewerPlayerId match
          case Some(playerId) => MahjongPlayerActionFunctions.legalActionsForPlayer(state, playerId)
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
  private[mahjongcore] def visibleTenpai(
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

  private[mahjongcore] def seatToView(
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

  private[mahjongcore] def shouldRevealSettlementHand(seat: MahjongSeatState, state: MahjongTableState): Boolean =
    state.currentRound.flatMap(_.result).exists(result => winningPlayerIds(result).contains(seat.playerId))

  private[mahjongcore] def roundToView(
      round: MahjongRoundState,
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId]
  ): MahjongRoundView =
    val viewerHasPendingCall = viewerPlayerId.exists(playerId =>
      round.pendingCall.exists(pending =>
        pending.candidates.exists(_.playerId == playerId) &&
          !pending.responses.exists(_.playerId == playerId)
      )
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

  private[mahjongcore] def eventToPublicView(event: MahjongEvent): MahjongPublicEventView =
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

  private[mahjongcore] def meldActionType(meldType: MahjongMeldType): PaifuActionType =
    meldType match
      case MahjongMeldType.Chi => PaifuActionType.Chi
      case MahjongMeldType.Pon => PaifuActionType.Pon
      case MahjongMeldType.OpenKan => PaifuActionType.OpenKan
      case MahjongMeldType.ClosedKan => PaifuActionType.ClosedKan
      case MahjongMeldType.AddedKan => PaifuActionType.AddedKan
