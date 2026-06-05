package riichinexus

import munit.FunSuite

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongGameLength, MahjongRoundPhase, MahjongRuleset, MahjongTableStatus, MahjongTableSticks}
import riichinexus.microservices.tournament.objects.paifumanagement.{HandOutcome, MahjongYakuKind, PaifuTile}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}

class MahjongCoreDomainSuite extends FunSuite:

  test("hand analysis recognizes a complete standard hand") {
    val tiles = Vector("1m", "2m", "3m", "1p", "2p", "3p", "1s", "2s", "3s", "1z", "1z", "1z", "2z", "2z").map(PaifuTile(_))

    assertEquals(MahjongHandAnalysisFunctions.calculateShanten(tiles), -1)
    assert(MahjongHandAnalysisFunctions.isWinning(tiles))
  }

  test("yaku analysis recognizes kokushi musou") {
    val winner = PlayerId("winner")
    val players = SeatWind.all.zipWithIndex.map { case (seat, index) => PlayerId(s"p$index") -> seat }.toMap + (winner -> SeatWind.East)
    val tiles = Vector("1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z", "1m").map(PaifuTile(_))

    val result = MahjongYakuAnalysisFunctions.analyzeWin(
      MahjongWinContext(
        winner = winner,
        target = None,
        seatByPlayer = players,
        roundWind = SeatWind.East,
        handTiles = tiles,
        melds = Vector.empty,
        winningTile = PaifuTile("1m"),
        doraIndicators = Vector(PaifuTile("3m"))
      )
    )

    assert(result.exists(_.yaku.exists(yaku => yaku.kind == MahjongYakuKind.KokushiMusou || yaku.kind == MahjongYakuKind.KokushiMusouThirteenWait)))
  }

  test("started table deals four seats and exposes east discards") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-test"), MahjongRuleset(), seed = "mahjong-core-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val legalActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(state, east.playerId)

    assertEquals(state.seats.size, 4)
    assertEquals(east.handTiles.size + east.drawTile.size, 14)
    assert(legalActions.exists(_.commandType == MahjongCommandType.Discard))
  }

  test("pending call view is only exposed to the responding viewer") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-call-view-test"), MahjongRuleset(), seed = "mahjong-core-call-view-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val discardedTile = PaifuTile("3m")
    val chiAction = MahjongLegalAction(
      commandType = MahjongCommandType.Chi,
      tile = Some(discardedTile),
      tiles = Vector("2m", "3m", "4m").map(PaifuTile(_)),
      fromPlayerId = Some(east.playerId),
      targetSequenceNo = Some(100),
      priority = 40
    )
    val pendingCallState = state.copy(
      status = MahjongTableStatus.WaitingCallDecision,
      currentRound = state.currentRound.map(round =>
        round.copy(
          phase = MahjongRoundPhase.CallDecision,
          pendingCall = Some(MahjongPendingCallState(
            discardSequenceNo = 100,
            discardPlayerId = east.playerId,
            tile = discardedTile,
            candidates = Vector(MahjongCallCandidate(south.playerId, Vector(chiAction)))
          ))
        )
      )
    )

    val callerView = MahjongGameStateTransitionFunctions.toView(pendingCallState, Some(south.playerId), includeLegalActions = true)
    val discarderView = MahjongGameStateTransitionFunctions.toView(pendingCallState, Some(east.playerId), includeLegalActions = true)
    val publicView = MahjongGameStateTransitionFunctions.toView(pendingCallState, viewerPlayerId = None, includeLegalActions = true)

    assert(callerView.currentRound.flatMap(_.pendingCall).nonEmpty)
    assertEquals(callerView.currentRound.flatMap(_.pendingCall).map(_.waitingPlayerIds), Some(Vector.empty))
    assert(callerView.legalActions.exists(_.commandType == MahjongCommandType.Chi))
    assert(callerView.legalActions.exists(_.commandType == MahjongCommandType.Pass))
    assertEquals(discarderView.currentRound.flatMap(_.pendingCall), None)
    assertEquals(discarderView.legalActions, Vector.empty)
    assertEquals(publicView.currentRound.flatMap(_.pendingCall), None)
    assertEquals(publicView.legalActions, Vector.empty)
    assert(publicView.seats.forall(_.handTiles.isEmpty))
  }

  test("double ron records one win result per winner and aggregates score changes") {
    val state = preparedRonState(MahjongRuleset(doubleRon = true), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val west = state.seats.find(_.seat == SeatWind.West).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (afterSouthRon, firstEvent) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterSouthRon,
      MahjongSubmittedAction(west.playerId, MahjongCommandType.Ron)
    )
    val result = finished.currentRound.flatMap(_.result).get

    assertEquals(firstEvent, None)
    assertEquals(finished.status, MahjongTableStatus.RoundEnded)
    assertEquals(result.outcome, HandOutcome.Ron)
    assertEquals(result.wins.map(_.winner), Vector(south.playerId, west.playerId))
    assertEquals(result.scoreChanges.map(_.playerId).distinct.size, 4)
    assertEquals(result.points, result.wins.map(_.points).sum)
    assert(result.scoreChanges.find(_.playerId == east.playerId).exists(_.delta < 0))
  }

  test("round result view reveals winning hands without exposing other concealed hands") {
    val state = preparedRonState(MahjongRuleset(doubleRon = false), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val publicView = MahjongGameStateTransitionFunctions.toView(finished, viewerPlayerId = None, includeLegalActions = false)
    val winnerView = publicView.seats.find(_.playerId == south.playerId).get

    assertEquals(finished.status, MahjongTableStatus.RoundEnded)
    assert(winnerView.handTiles.exists(_.size == 13))
    assert(winnerView.handTiles.exists(tiles => !tiles.contains(PaifuTile("3m"))))
    assert(publicView.seats.filterNot(_.playerId == south.playerId).forall(_.handTiles.isEmpty))
  }

  test("seat tenpai is only visible to the seat owner before draw settlement") {
    val state = preparedRonState(MahjongRuleset(doubleRon = false), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val eastView = MahjongGameStateTransitionFunctions.toView(state, Some(east.playerId), includeLegalActions = true)
    val southView = MahjongGameStateTransitionFunctions.toView(state, Some(south.playerId), includeLegalActions = true)
    val publicView = MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = false)

    assertEquals(eastView.seats.find(_.playerId == south.playerId).flatMap(_.tenpai), None)
    assertEquals(publicView.seats.find(_.playerId == south.playerId).flatMap(_.tenpai), None)
    assertEquals(southView.seats.find(_.playerId == south.playerId).flatMap(_.tenpai), Some(true))
  }

  test("triple ron abortive draw ends the hand without score transfer when enabled") {
    val state = preparedRonState(MahjongRuleset(doubleRon = true, tripleRonAbortiveDraw = true))
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val west = state.seats.find(_.seat == SeatWind.West).get
    val north = state.seats.find(_.seat == SeatWind.North).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (afterSouthRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val (afterWestRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterSouthRon,
      MahjongSubmittedAction(west.playerId, MahjongCommandType.Ron)
    )
    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterWestRon,
      MahjongSubmittedAction(north.playerId, MahjongCommandType.Ron)
    )
    val result = finished.currentRound.flatMap(_.result).get

    assertEquals(finished.status, MahjongTableStatus.RoundEnded)
    assertEquals(result.outcome, HandOutcome.AbortiveDraw)
    assert(result.wins.isEmpty)
    assert(result.scoreChanges.forall(_.delta == 0))
    assert(result.settlement.exists(_.notes.contains("三家和流局")))
  }

  test("nagashi mangan settles as mangan tsumo when exhaustive draw is reached") {
    val state = preparedNagashiManganState()
    val east = state.seats.find(_.seat == SeatWind.East).get

    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("7z")))
    )
    val result = finished.currentRound.flatMap(_.result).get

    assertEquals(finished.status, MahjongTableStatus.RoundEnded)
    assertEquals(result.outcome, HandOutcome.Tsumo)
    assertEquals(result.winner, Some(east.playerId))
    assertEquals(result.wins.map(_.winner), Vector(east.playerId))
    assert(result.yaku.exists(_.kind == MahjongYakuKind.NagashiMangan))
    assertEquals(result.scoreChanges.find(_.playerId == east.playerId).map(_.delta), Some(12000))
    assert(result.scoreChanges.filterNot(_.playerId == east.playerId).forall(_.delta == -4000))
  }

  test("accepted riichi declaration pays one stick into the table") {
    val state = preparedRiichiDeclarationState()
    val east = state.seats.find(_.seat == SeatWind.East).get

    val (afterRiichi, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Riichi, tile = Some(PaifuTile("9m")))
    )
    val updatedEast = afterRiichi.seats.find(_.playerId == east.playerId).get

    assertEquals(updatedEast.points, 24000)
    assert(updatedEast.riichi)
    assertEquals(afterRiichi.sticks.riichi, 1)
  }

  test("win settlement adds riichi deposits and honba payments to score changes") {
    val ruleset = MahjongRuleset(doubleRon = false)
    val baseState = preparedRonState(ruleset, northCanRon = false)
    val east = baseState.seats.find(_.seat == SeatWind.East).get
    val south = baseState.seats.find(_.seat == SeatWind.South).get
    val (baseWaitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      baseState,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (baseFinished, _) = MahjongGameStateTransitionFunctions.submitAction(
      baseWaitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val baseResult = baseFinished.currentRound.flatMap(_.result).get
    val baseSouthDelta = baseResult.scoreChanges.find(_.playerId == south.playerId).map(_.delta).get
    val baseEastDelta = baseResult.scoreChanges.find(_.playerId == east.playerId).map(_.delta).get

    val stateWithSticks = withRoundSticks(baseState, MahjongTableSticks(honba = 2, riichi = 2))
    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      stateWithSticks,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val result = finished.currentRound.flatMap(_.result).get

    assertEquals(result.points, baseResult.points)
    assertEquals(result.settlement.map(_.riichiSticksDelta), Some(2000))
    assertEquals(result.settlement.map(_.honbaPayment), Some(600))
    assertEquals(result.scoreChanges.find(_.playerId == south.playerId).map(_.delta), Some(baseSouthDelta + 2600))
    assertEquals(result.scoreChanges.find(_.playerId == east.playerId).map(_.delta), Some(baseEastDelta - 600))
    assertEquals(finished.sticks.riichi, 0)
  }

  test("normalized event replay expires ippatsu after riichi player's next discard") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-ippatsu-test"), MahjongRuleset(), seed = "mahjong-core-ippatsu-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val west = state.seats.find(_.seat == SeatWind.West).get
    val north = state.seats.find(_.seat == SeatWind.North).get
    val descriptor = state.currentRound.get.descriptor
    val events = Vector(
      MahjongEvent.TableStarted(1),
      MahjongEvent.RoundStarted(2, descriptor),
      MahjongEvent.TileDrawn(3, east.playerId, PaifuTile("5z")),
      MahjongEvent.RiichiDeclared(4, east.playerId, PaifuTile("5z")),
      MahjongEvent.TileDiscarded(5, east.playerId, PaifuTile("5z"), tsumogiri = true),
      MahjongEvent.TileDrawn(6, south.playerId, PaifuTile("1z")),
      MahjongEvent.TileDiscarded(7, south.playerId, PaifuTile("1z"), tsumogiri = true),
      MahjongEvent.TileDrawn(8, west.playerId, PaifuTile("2z")),
      MahjongEvent.TileDiscarded(9, west.playerId, PaifuTile("2z"), tsumogiri = true),
      MahjongEvent.TileDrawn(10, north.playerId, PaifuTile("3z")),
      MahjongEvent.TileDiscarded(11, north.playerId, PaifuTile("3z"), tsumogiri = true),
      MahjongEvent.TileDrawn(12, east.playerId, PaifuTile("4z")),
      MahjongEvent.TileDiscarded(13, east.playerId, PaifuTile("4z"), tsumogiri = true)
    )
    val eventState = state.copy(
      currentRound = state.currentRound.map(_.copy(events = events)),
      seats = state.seats.map(_.copy(drawTile = None, river = Vector.empty, riichi = false, ippatsu = false, furiten = false))
    )

    val normalized = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(eventState)
    val normalizedEast = normalized.seats.find(_.playerId == east.playerId).get
    val result = MahjongYakuAnalysisFunctions.analyzeWin(
      MahjongWinContext(
        winner = east.playerId,
        target = None,
        seatByPlayer = normalized.seats.map(seat => seat.playerId -> seat.seat).toMap,
        roundWind = descriptor.roundWind,
        handTiles = tiles("2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s", "6m", "7m", "8m", "5z", "5z"),
        melds = Vector.empty,
        winningTile = PaifuTile("5z"),
        doraIndicators = Vector(PaifuTile("3m")),
        riichi = normalizedEast.riichi,
        ippatsu = normalizedEast.ippatsu
      )
    ).get

    assert(normalizedEast.riichi)
    assert(!normalizedEast.ippatsu)
    assert(result.yaku.exists(_.kind == MahjongYakuKind.Riichi))
    assert(!result.yaku.exists(_.kind == MahjongYakuKind.Ippatsu))
  }

  test("advanceRound starts the next hand for an unfinished tonpu table") {
    val state = preparedRonState(MahjongRuleset(gameLength = MahjongGameLength.Tonpu, doubleRon = false), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)
    val nextRound = advanced.currentRound.get

    assertEquals(roundEnded.status, MahjongTableStatus.RoundEnded)
    assertEquals(advanced.status, MahjongTableStatus.WaitingPlayerAction)
    assertEquals(advanced.finishedRounds.size, 1)
    assertEquals(nextRound.descriptor.roundWind, SeatWind.East)
    assertEquals(nextRound.descriptor.handNumber, 2)
    assertEquals(nextRound.result, None)
    assertEquals(nextRound.turnPlayerId, south.playerId)
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.playerId), Some(south.playerId))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(seat => seat.handTiles.size + seat.drawTile.size), Some(14))
    assert(advanced.seats.filterNot(_.seat == SeatWind.East).forall(seat => seat.handTiles.size + seat.drawTile.size == 13))
  }

  private def preparedRonState(ruleset: MahjongRuleset, northCanRon: Boolean = true) =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-ron-test"), ruleset, seed = "mahjong-core-ron-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val ronHand = tiles("1m", "2m", "4m", "5m", "6m", "1p", "2p", "3p", "1s", "2s", "3s", "7z", "7z")
    val quietHand = tiles("2m", "2m", "4m", "5m", "6m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "6z")
    val seats = state.seats.map { seat =>
      seat.seat match
        case SeatWind.East =>
          seat.copy(handTiles = quietHand, drawTile = Some(PaifuTile("3m")), riichi = false, ippatsu = false, furiten = false)
        case SeatWind.South | SeatWind.West =>
          seat.copy(handTiles = ronHand, drawTile = None, riichi = true, ippatsu = false, furiten = false)
        case SeatWind.North if northCanRon =>
          seat.copy(handTiles = ronHand, drawTile = None, riichi = true, ippatsu = false, furiten = false)
        case SeatWind.North =>
          seat.copy(handTiles = quietHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
    }
    state.copy(
      seats = seats,
      status = MahjongTableStatus.WaitingPlayerAction,
      currentRound = state.currentRound.map(round =>
        round.copy(
          phase = MahjongRoundPhase.PlayerTurn,
          turnPlayerId = east.playerId,
          pendingCall = None,
          wall = tiles("5z", "6z", "7z")
        )
      )
    )

  private def preparedNagashiManganState() =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-nagashi-test"), MahjongRuleset(nagashiMangan = true), seed = "mahjong-core-nagashi-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val quietHand = tiles("2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "5z", "6z", "6z")
    val seats = state.seats.map { seat =>
      if seat.seat == SeatWind.East then
        seat.copy(
          handTiles = quietHand,
          drawTile = Some(PaifuTile("7z")),
          river = Vector(
            MahjongDiscard(10, seat.playerId, PaifuTile("9m"), tsumogiri = true),
            MahjongDiscard(20, seat.playerId, PaifuTile("1p"), tsumogiri = true),
            MahjongDiscard(30, seat.playerId, PaifuTile("1s"), tsumogiri = true)
          ),
          riichi = false,
          ippatsu = false,
          furiten = false
        )
      else
        seat.copy(handTiles = quietHand, drawTile = None, river = Vector.empty, riichi = false, ippatsu = false, furiten = false)
    }
    state.copy(
      seats = seats,
      status = MahjongTableStatus.WaitingPlayerAction,
      currentRound = state.currentRound.map(round =>
        round.copy(
          phase = MahjongRoundPhase.PlayerTurn,
          turnPlayerId = east.playerId,
          pendingCall = None,
          wall = Vector.empty
        )
      )
    )

  private def preparedRiichiDeclarationState() =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-riichi-stick-test"), MahjongRuleset(), seed = "mahjong-core-riichi-stick-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val riichiHand = tiles("1m", "2m", "3m", "1p", "2p", "3p", "1s", "2s", "3s", "4m", "5m", "7z", "7z")
    val quietHand = tiles("2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "5z", "6z", "6z")
    state.copy(
      seats = state.seats.map { seat =>
        if seat.seat == SeatWind.East then
          seat.copy(handTiles = riichiHand, drawTile = Some(PaifuTile("9m")), riichi = false, ippatsu = false, furiten = false)
        else seat.copy(handTiles = quietHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
      },
      status = MahjongTableStatus.WaitingPlayerAction,
      currentRound = state.currentRound.map(round =>
        round.copy(
          phase = MahjongRoundPhase.PlayerTurn,
          turnPlayerId = east.playerId,
          pendingCall = None,
          wall = tiles("5z", "6z", "7z")
        )
      )
    )

  private def withRoundSticks(state: MahjongTableState, sticks: MahjongTableSticks): MahjongTableState =
    state.copy(
      sticks = sticks,
      currentRound = state.currentRound.map(round =>
        round.copy(
          descriptor = round.descriptor.copy(honba = sticks.honba),
          roundStartSticks = sticks
        )
      )
    )

  private def tiles(values: String*): Vector[PaifuTile] =
    values.toVector.map(PaifuTile(_))
