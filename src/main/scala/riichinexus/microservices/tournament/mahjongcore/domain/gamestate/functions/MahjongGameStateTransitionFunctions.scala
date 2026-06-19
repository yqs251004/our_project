package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongSubmittedAction, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.sortTiles
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongLegalAction, MahjongPublicEventView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongRuleset, MahjongTableStatus, MahjongTableView}
import riichinexus.microservices.tournament.objects.tablemanagement.{TableId, TableSeat}

/** MahjongGameStateTransitionFunctions 提供麻将游戏状态转换相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongGameStateTransitionFunctions:
  import MahjongGameStateSupport.{applyMeldEvent, replaceSeat, seatByPlayerId}

  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String): MahjongTableState =
    MahjongRoundLifecycleFunctions.startTable(tableId, ruleset, seed)

  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String, tableSeats: Vector[TableSeat]): MahjongTableState =
    MahjongRoundLifecycleFunctions.startTable(tableId, ruleset, seed, tableSeats)

  def notStartedTable(tableId: TableId, ruleset: MahjongRuleset = MahjongRuleset()): MahjongTableState =
    MahjongRoundLifecycleFunctions.notStartedTable(tableId, ruleset)

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
              furiten = updatedSeatWithoutDiscard.furiten || MahjongPlayerActionFunctions.isWinningOwnDiscard(updatedSeatWithoutDiscard, normalizedTile)
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
    MahjongRoundLifecycleFunctions.advanceRound(state, showcaseMode)

  def submitAction(
      state: MahjongTableState,
      action: MahjongSubmittedAction
  ): (MahjongTableState, Option[MahjongPublicEventView]) =
    MahjongPlayerActionFunctions.submitAction(state, action)

  def archiveTable(state: MahjongTableState): MahjongTableState =
    state.copy(status = MahjongTableStatus.Archived, version = state.version + 1)

  def toView(
      state: MahjongTableState,
      viewerPlayerId: Option[PlayerId],
      includeLegalActions: Boolean,
      revealAllHands: Boolean = false
  ): MahjongTableView =
    MahjongGameStateViewFunctions.toView(state, viewerPlayerId, includeLegalActions, revealAllHands)

  def legalActionsForPlayer(state: MahjongTableState, playerId: PlayerId): Vector[MahjongLegalAction] =
    MahjongPlayerActionFunctions.legalActionsForPlayer(state, playerId)
