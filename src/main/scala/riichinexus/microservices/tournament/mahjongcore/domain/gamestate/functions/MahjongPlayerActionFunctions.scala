package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongSeatState, MahjongSubmittedAction, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{indexOf, isRed}
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction, MahjongPublicEventView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRoundPhase
import riichinexus.microservices.tournament.objects.paifu.{PaifuTile, RoundSettlementNote}

import MahjongGameStateSupport.{matchesSubmittedAction, seatByPlayerId}
import MahjongGameStateViewFunctions.eventToPublicView
import MahjongWinSettlementFunctions.{declareTsumo, winContext}

/** MahjongPlayerActionFunctions 提供麻将玩家动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongPlayerActionFunctions:
  def submitAction(
      state: MahjongTableState,
      action: MahjongSubmittedAction
  ): (MahjongTableState, Option[MahjongPublicEventView]) =
    val legalAction = legalActionsForPlayer(state, action.playerId).find(matchesSubmittedAction(_, action))
      .getOrElse(throw IllegalArgumentException(s"Illegal mahjong action ${action.commandType} for ${action.playerId.value}"))

    val (nextState, event) =
      action.commandType match
        case MahjongCommandType.Pass => MahjongCallActionFunctions.recordCallResponse(state, action.playerId, legalAction)
        case MahjongCommandType.Discard => MahjongDiscardActionFunctions.discard(state, action.playerId, action.tile.orElse(legalAction.tile).get, riichiDeclared = false)
        case MahjongCommandType.Riichi => MahjongDiscardActionFunctions.discard(state, action.playerId, action.tile.orElse(legalAction.tile).get, riichiDeclared = true)
        case MahjongCommandType.Tsumo => declareTsumo(state, action.playerId)
        case MahjongCommandType.Ron => MahjongCallActionFunctions.recordCallResponse(state, action.playerId, legalAction)
        case MahjongCommandType.Chi | MahjongCommandType.Pon | MahjongCommandType.OpenKan => MahjongCallActionFunctions.recordCallResponse(state, action.playerId, legalAction)
        case MahjongCommandType.ClosedKan => MahjongKanActionFunctions.closedKan(state, action.playerId, legalAction)
        case MahjongCommandType.AddedKan => MahjongKanActionFunctions.addedKan(state, action.playerId, legalAction)
        case MahjongCommandType.AbortiveDraw => MahjongDrawActionFunctions.abortiveDraw(state, Some(RoundSettlementNote.AbortiveDrawRequested))

    (nextState.copy(version = nextState.version + 1), event.map(eventToPublicView))

  def legalActionsForPlayer(state: MahjongTableState, playerId: PlayerId): Vector[MahjongLegalAction] =
    state.currentRound match
      case None => Vector.empty
      case Some(round) =>
        round.pendingCall.flatMap(_.candidates.find(_.playerId == playerId)) match
          case Some(candidate) if !round.pendingCall.exists(_.responses.exists(_.playerId == playerId)) =>
            val pending = round.pendingCall.get
            val activeActions = MahjongCallActionFunctions.legalActionsForCandidate(state, pending, candidate)
            if activeActions.isEmpty then Vector.empty
            else
              activeActions :+ MahjongLegalAction(
                commandType = MahjongCommandType.Pass,
                tile = Some(pending.tile),
                fromPlayerId = Some(pending.discardPlayerId),
                targetSequenceNo = Some(pending.discardSequenceNo),
                priority = 0
              )
          case Some(_) => Vector.empty
          case None if round.turnPlayerId == playerId && round.phase == MahjongRoundPhase.PlayerTurn =>
            currentTurnActions(state, round, seatByPlayerId(state, playerId))
          case _ => Vector.empty

  private[mahjongcore] def currentTurnActions(
      state: MahjongTableState,
      round: MahjongRoundState,
      seat: MahjongSeatState
  ): Vector[MahjongLegalAction] =
    val discardTiles =
      if seat.riichi then seat.drawTile.toVector.map(MahjongTileFunctions.normalize)
      else (seat.handTiles ++ seat.drawTile.toVector).map(MahjongTileFunctions.normalize).distinctBy(tile => (indexOf(tile), isRed(tile)))
    val discardActions = discardTiles.map(tile => MahjongLegalAction(MahjongCommandType.Discard, tile = Some(tile), priority = 10))
    val riichiActions =
      if MahjongRiichiActionFunctions.canDeclareRiichi(seat) then
        discardTiles.filter(tile => MahjongRiichiActionFunctions.leavesTenpaiAfterDiscard(seat, tile)).map { tile =>
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
    val closedKanActions = MahjongKanActionFunctions.closedKanLegalActions(seat)
    val addedKanActions = MahjongKanActionFunctions.addedKanLegalActions(seat)
    tsumoActions ++ closedKanActions ++ addedKanActions ++ riichiActions ++ discardActions

  private[mahjongcore] def isWinningOwnDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    MahjongDiscardActionFunctions.isWinningOwnDiscard(seat, discardTile)
