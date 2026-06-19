package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{TileTypeCount, countsOf, indexOf, sortTiles, tileOf, tilesFromCounts}
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongMeldType, MahjongRoundPhase, MahjongTableStatus}

import MahjongGameStateSupport.{nextSequenceNo, replaceSeat, requireRound, seatByPlayerId}

/** MahjongKanActionFunctions 提供麻将杠动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongKanActionFunctions:
  private[mahjongcore] def closedKan(
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

  private[mahjongcore] def addedKan(
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

  private[mahjongcore] def drawReplacementAfterKan(
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

  private[mahjongcore] def closedKanLegalActions(seat: MahjongSeatState): Vector[MahjongLegalAction] =
    val counts = countsOf(seat.handTiles ++ seat.drawTile.toVector)
    (0 until TileTypeCount).toVector.flatMap { index =>
      if counts(index) == 4 && (!seat.riichi || riichiClosedKanKeepsWaits(seat, index)) then
        Vector(MahjongLegalAction(MahjongCommandType.ClosedKan, tile = Some(tileOf(index)), tiles = Vector.fill(4)(tileOf(index)), priority = 70))
      else Vector.empty
    }

  private[mahjongcore] def addedKanLegalActions(seat: MahjongSeatState): Vector[MahjongLegalAction] =
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

  private[mahjongcore] def riichiClosedKanKeepsWaits(seat: MahjongSeatState, kanIndex: Int): Boolean =
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
