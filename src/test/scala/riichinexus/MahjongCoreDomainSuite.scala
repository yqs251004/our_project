package riichinexus.microservices.tournament

import munit.FunSuite

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.api.{MahjongCoreAdvanceRoundAPIMessage, MahjongCoreSetShowcaseModeAPIMessage}
import riichinexus.microservices.tournament.mahjongcore.domain.MahjongCoreShowcaseMode
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.*
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.SetMahjongCoreShowcaseModeRequest
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongGameLength, MahjongMeld, MahjongMeldType, MahjongRoundPhase, MahjongRuleset, MahjongTableStatus, MahjongTableSticks, MahjongTableView}
import riichinexus.microservices.tournament.objects.paifumanagement.{AgariResult, HandOutcome, MahjongYakuKind, PaifuActionType, PaifuTile}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}
import upickle.default.{read, write}

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

  test("yakuman settlement handles non-dealer ron and dealer tsumo payments") {
    val east = PlayerId("yakuman-east")
    val south = PlayerId("yakuman-south")
    val west = PlayerId("yakuman-west")
    val north = PlayerId("yakuman-north")
    val seatByPlayer = Map(
      east -> SeatWind.East,
      south -> SeatWind.South,
      west -> SeatWind.West,
      north -> SeatWind.North
    )
    val normalKokushiHand = tiles("1m", "1m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z")

    val childRon = MahjongYakuAnalysisFunctions.analyzeWin(
      MahjongWinContext(
        winner = south,
        target = Some(east),
        seatByPlayer = seatByPlayer,
        roundWind = SeatWind.East,
        handTiles = normalKokushiHand,
        melds = Vector.empty,
        winningTile = PaifuTile("9m"),
        doraIndicators = Vector(PaifuTile("3m"))
      )
    ).get
    val dealerTsumo = MahjongYakuAnalysisFunctions.analyzeWin(
      MahjongWinContext(
        winner = east,
        target = None,
        seatByPlayer = seatByPlayer,
        roundWind = SeatWind.East,
        handTiles = normalKokushiHand,
        melds = Vector.empty,
        winningTile = PaifuTile("9m"),
        doraIndicators = Vector(PaifuTile("3m"))
      )
    ).get

    assertEquals(childRon.yaku.map(_.kind), Vector(MahjongYakuKind.KokushiMusou))
    assertEquals(childRon.points, 32000)
    assertEquals(scoreDelta(childRon, south), 32000)
    assertEquals(scoreDelta(childRon, east), -32000)
    assertEquals(scoreDelta(childRon, west), 0)
    assertEquals(dealerTsumo.yaku.map(_.kind), Vector(MahjongYakuKind.KokushiMusou))
    assertEquals(dealerTsumo.points, 48000)
    assertEquals(scoreDelta(dealerTsumo, east), 48000)
    assertEquals(scoreDelta(dealerTsumo, south), -16000)
    assertEquals(scoreDelta(dealerTsumo, west), -16000)
    assertEquals(scoreDelta(dealerTsumo, north), -16000)
  }

  test("multiple yakuman settlement can be capped by ruleset") {
    val east = PlayerId("triple-yakuman-east")
    val south = PlayerId("triple-yakuman-south")
    val west = PlayerId("triple-yakuman-west")
    val north = PlayerId("triple-yakuman-north")
    val seatByPlayer = Map(
      east -> SeatWind.East,
      south -> SeatWind.South,
      west -> SeatWind.West,
      north -> SeatWind.North
    )
    val thirteenWaitKokushi = tiles("1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z")

    def analyze(allowMultipleYakuman: Boolean) =
      MahjongYakuAnalysisFunctions.analyzeWin(
        MahjongWinContext(
          winner = east,
          target = None,
          seatByPlayer = seatByPlayer,
          roundWind = SeatWind.East,
          handTiles = thirteenWaitKokushi,
          melds = Vector.empty,
          winningTile = PaifuTile("1m"),
          doraIndicators = Vector(PaifuTile("3m")),
          ruleset = MahjongRuleset(allowMultipleYakuman = allowMultipleYakuman),
          tenhou = true
        )
      ).get

    val uncapped = analyze(allowMultipleYakuman = true)
    val capped = analyze(allowMultipleYakuman = false)

    assertEquals(uncapped.yaku.map(_.kind).toSet, Set(MahjongYakuKind.KokushiMusouThirteenWait, MahjongYakuKind.Tenhou))
    assertEquals(uncapped.han, Some(39))
    assertEquals(uncapped.points, 144000)
    assertEquals(scoreDelta(uncapped, east), 144000)
    assertEquals(scoreDelta(uncapped, south), -48000)
    assertEquals(uncapped.settlement.flatMap(_.notes.headOption), Some("3倍役满"))
    assertEquals(capped.han, Some(39))
    assertEquals(capped.points, 48000)
    assertEquals(scoreDelta(capped, east), 48000)
    assertEquals(scoreDelta(capped, south), -16000)
    assertEquals(capped.settlement.flatMap(_.notes.headOption), Some("役满"))
  }

  test("started table deals four seats and exposes east discards") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-test"), MahjongRuleset(), seed = "mahjong-core-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val legalActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(state, east.playerId)

    assertEquals(state.seats.size, 4)
    assertEquals(east.handTiles.size + east.drawTile.size, 14)
    assert(legalActions.exists(_.commandType == MahjongCommandType.Discard))
  }

  test("startTable is deterministic by seed and accounts for every physical tile") {
    val first = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-determinism"), MahjongRuleset(), seed = "same-seed")
    val second = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-determinism"), MahjongRuleset(), seed = "same-seed")
    val different = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-determinism"), MahjongRuleset(), seed = "different-seed")
    val firstTiles = physicalTiles(first)
    val normalizedCounts = firstTiles.groupBy(indexOf).view.mapValues(_.size).toMap

    assertEquals(first.seats.map(_.handTiles), second.seats.map(_.handTiles))
    assertEquals(first.seats.map(_.drawTile), second.seats.map(_.drawTile))
    assertEquals(first.currentRound.map(_.wall), second.currentRound.map(_.wall))
    assertNotEquals(first.seats.map(_.handTiles), different.seats.map(_.handTiles))
    assertEquals(firstTiles.size, 136)
    assertEquals(normalizedCounts.size, TileTypeCount)
    assert((0 until TileTypeCount).forall(index => normalizedCounts.getOrElse(index, 0) == 4))
  }

  test("public and player table views serialize cleanly while preserving hand privacy") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-view-json"), MahjongRuleset(), seed = "mahjong-core-view-json-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val publicView = MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = true)
    val playerView = MahjongGameStateTransitionFunctions.toView(state, Some(east.playerId), includeLegalActions = true)
    val decodedPublicView = read[MahjongTableView](write(publicView))
    val decodedPlayerView = read[MahjongTableView](write(playerView))

    assertEquals(decodedPublicView.tableId, state.tableId)
    assertEquals(decodedPublicView.legalActions, Vector.empty)
    assert(decodedPublicView.seats.forall(_.handTiles.isEmpty))
    assert(decodedPublicView.seats.forall(_.drawTile.isEmpty))
    assertEquals(decodedPublicView.seats.map(_.handTileCount).sum, 53)
    assert(decodedPlayerView.seats.find(_.playerId == east.playerId).flatMap(_.handTiles).exists(_.size == 14))
    assert(decodedPlayerView.seats.find(_.playerId == east.playerId).flatMap(_.drawTile).nonEmpty)
    assert(decodedPlayerView.legalActions.exists(_.commandType == MahjongCommandType.Discard))
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

  test("same responder can choose both chi and pon when both are legal") {
    val state = preparedChiPonState()
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val (pendingCallState, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val southActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(pendingCallState, south.playerId)

    assert(southActions.exists(_.commandType == MahjongCommandType.Pon))
    assert(southActions.exists(_.commandType == MahjongCommandType.Chi))
    assert(southActions.exists(_.commandType == MahjongCommandType.Pass))
  }

  test("same responder can choose ron or chi when both are legal") {
    val base = preparedChiPonState()
    val east = base.seats.find(_.seat == SeatWind.East).get
    val south = base.seats.find(_.seat == SeatWind.South).get
    val state = base.copy(
      seats = base.seats.map {
        case seat if seat.seat == SeatWind.South =>
          seat.copy(handTiles = ronTenpaiHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
        case seat => seat
      }
    )

    val (pendingCallState, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val southActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(pendingCallState, south.playerId)

    assert(southActions.exists(_.commandType == MahjongCommandType.Ron))
    assert(southActions.exists(_.commandType == MahjongCommandType.Chi))
    assert(southActions.exists(_.commandType == MahjongCommandType.Pass))
  }

  test("call decisions wait for tied highest-priority responses but not lower-priority calls") {
    val state = preparedRonState(MahjongRuleset(doubleRon = true), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val west = state.seats.find(_.seat == SeatWind.West).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val westActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(waitingRon, west.playerId)

    assert(westActions.exists(_.commandType == MahjongCommandType.Ron))
    val (afterSouthRon, firstEvent) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Ron)
    )

    assertEquals(afterSouthRon.status, MahjongTableStatus.WaitingCallDecision)
    assertEquals(firstEvent, None)
    assertEquals(MahjongGameStateTransitionFunctions.legalActionsForPlayer(afterSouthRon, south.playerId), Vector.empty)
    assert(MahjongGameStateTransitionFunctions.legalActionsForPlayer(afterSouthRon, west.playerId).exists(_.commandType == MahjongCommandType.Ron))

    val (finished, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterSouthRon,
      MahjongSubmittedAction(west.playerId, MahjongCommandType.Pass)
    )

    assertEquals(finished.status, MahjongTableStatus.RoundEnded)
    assertEquals(finished.currentRound.flatMap(_.result).flatMap(_.winner), Some(south.playerId))
  }

  test("pon preserves a red five discard in the public meld tiles") {
    val base = preparedChiPonState()
    val east = base.seats.find(_.seat == SeatWind.East).get
    val south = base.seats.find(_.seat == SeatWind.South).get
    val state = base.copy(
      seats = base.seats.map {
        case seat if seat.seat == SeatWind.East =>
          seat.copy(drawTile = Some(PaifuTile("0p")))
        case seat if seat.seat == SeatWind.South =>
          seat.copy(handTiles = tiles("5p", "5p", "2m", "3m", "4m", "2s", "3s", "4s", "5z", "5z", "6z", "6z", "7z"))
        case seat => seat
      }
    )

    val (pendingCallState, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("0p")))
    )
    val ponAction = MahjongGameStateTransitionFunctions
      .legalActionsForPlayer(pendingCallState, south.playerId)
      .find(_.commandType == MahjongCommandType.Pon)
      .get
    val (calledState, _) = MahjongGameStateTransitionFunctions.submitAction(
      pendingCallState,
      MahjongSubmittedAction(
        south.playerId,
        MahjongCommandType.Pon,
        tile = ponAction.tile,
        tiles = ponAction.tiles,
        targetSequenceNo = ponAction.targetSequenceNo
      )
    )
    val meld = calledState.seats.find(_.playerId == south.playerId).get.melds.head

    assertEquals(meld.meldType, MahjongMeldType.Pon)
    assertEquals(meld.calledTile, Some(PaifuTile("0p")))
    assertEquals(meld.tiles.size, 3)
    assert(meld.tiles.contains(PaifuTile("0p")))
  }

  test("chi call resolves the discard relation, turn owner, meld surface and caller discard options") {
    val state = preparedChiPonState()
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val (pendingCallState, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val chiAction = MahjongGameStateTransitionFunctions
      .legalActionsForPlayer(pendingCallState, south.playerId)
      .find(_.commandType == MahjongCommandType.Chi)
      .get
    val (calledState, acceptedEvent) = MahjongGameStateTransitionFunctions.submitAction(
      pendingCallState,
      MahjongSubmittedAction(
        south.playerId,
        MahjongCommandType.Chi,
        tile = chiAction.tile,
        tiles = chiAction.tiles,
        targetSequenceNo = chiAction.targetSequenceNo
      )
    )
    val calledEast = calledState.seats.find(_.playerId == east.playerId).get
    val calledSouth = calledState.seats.find(_.playerId == south.playerId).get
    val publicView = MahjongGameStateTransitionFunctions.toView(calledState, viewerPlayerId = None, includeLegalActions = false)

    assertEquals(calledState.status, MahjongTableStatus.WaitingPlayerAction)
    assertEquals(calledState.currentRound.map(_.turnPlayerId), Some(south.playerId))
    assertEquals(calledState.currentRound.flatMap(_.pendingCall), None)
    assert(acceptedEvent.exists(_.actionType == PaifuActionType.Chi))
    assertEquals(calledEast.river.last.calledBy, Some(south.playerId))
    assertEquals(calledSouth.melds.size, 1)
    assertEquals(calledSouth.melds.head.meldType, MahjongMeldType.Chi)
    assertEquals(calledSouth.melds.head.fromPlayer, Some(east.playerId))
    assert(calledState.seats.forall(!_.ippatsu))
    assert(MahjongGameStateTransitionFunctions.legalActionsForPlayer(calledState, south.playerId).exists(_.commandType == MahjongCommandType.Discard))
    assertEquals(publicView.seats.find(_.playerId == east.playerId).flatMap(_.river.lastOption.flatMap(_.calledBy)), Some(south.playerId))
    assertEquals(publicView.seats.find(_.playerId == south.playerId).map(_.melds.size), Some(1))
  }

  test("illegal action from a non-turn player is rejected without advancing version") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-illegal-action"), MahjongRuleset(), seed = "mahjong-core-illegal-action-seed")
    val south = state.seats.find(_.seat == SeatWind.South).get

    intercept[IllegalArgumentException] {
      MahjongGameStateTransitionFunctions.submitAction(
        state,
        MahjongSubmittedAction(south.playerId, MahjongCommandType.Discard, tile = south.handTiles.headOption)
      )
    }

    assertEquals(state.version, 1)
    assertEquals(state.status, MahjongTableStatus.WaitingPlayerAction)
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

    val (afterRiichi, acceptedEvent) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Riichi, tile = Some(PaifuTile("9m")))
    )
    val updatedEast = afterRiichi.seats.find(_.playerId == east.playerId).get

    assertEquals(updatedEast.points, 24000)
    assert(updatedEast.riichi)
    assertEquals(afterRiichi.sticks.riichi, 1)
    assertEquals(acceptedEvent.map(_.actionType), Some(PaifuActionType.Riichi))
  }

  test("riichi player becomes furiten after passing ron but can still tsumo") {
    val state = preparedSingleRiichiRonState()
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get

    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (afterPass, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Pass)
    )
    val furitenSouth = afterPass.seats.find(_.playerId == south.playerId).get
    val normalizedSouth = MahjongGameStateTransitionFunctions
      .normalizeCurrentRoundState(afterPass)
      .seats
      .find(_.playerId == south.playerId)
      .get

    assert(furitenSouth.furiten)
    assert(normalizedSouth.furiten)
    assertEquals(afterPass.status, MahjongTableStatus.WaitingPlayerAction)

    val (afterSouthDiscard, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterPass,
      MahjongSubmittedAction(south.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("5z")))
    )
    val west = afterSouthDiscard.seats.find(_.seat == SeatWind.West).get
    val (afterWestDiscard, _) = MahjongGameStateTransitionFunctions.submitAction(
      afterSouthDiscard,
      MahjongSubmittedAction(west.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )

    assert(!MahjongGameStateTransitionFunctions.legalActionsForPlayer(afterWestDiscard, south.playerId).exists(_.commandType == MahjongCommandType.Ron))
    assert(afterWestDiscard.seats.find(_.playerId == south.playerId).exists(_.furiten))

    val tsumoState = afterPass.copy(
      seats = afterPass.seats.map { seat =>
        if seat.playerId == south.playerId then seat.copy(handTiles = ronTenpaiHand, drawTile = Some(PaifuTile("3m")), furiten = true)
        else seat.copy(drawTile = None)
      },
      currentRound = afterPass.currentRound.map(round =>
        round.copy(
          phase = MahjongRoundPhase.PlayerTurn,
          turnPlayerId = south.playerId,
          pendingCall = None
        )
      ),
      status = MahjongTableStatus.WaitingPlayerAction
    )
    val tsumoActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(tsumoState, south.playerId)

    assert(tsumoActions.exists(_.commandType == MahjongCommandType.Tsumo))
  }

  test("riichi closed kan is allowed when the drawn kan tile keeps waits") {
    val state = preparedRiichiClosedKanState(drawTile = "1m")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val legalActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(state, east.playerId)

    assert(legalActions.exists(action =>
      action.commandType == MahjongCommandType.ClosedKan &&
        action.tile.contains(PaifuTile("1m"))
    ))
  }

  test("riichi closed kan cannot use a concealed quad that was not just drawn") {
    val state = preparedRiichiClosedKanState(drawTile = "7z")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val legalActions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(state, east.playerId)

    assert(!legalActions.exists(action =>
      action.commandType == MahjongCommandType.ClosedKan &&
        action.tile.contains(PaifuTile("1m"))
    ))
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

  test("advance round API accepts frontend and legacy option JSON shapes") {
    val message = read[MahjongCoreAdvanceRoundAPIMessage](
      """{"tableId":"table-be548ec5","request":{"playerId":"player-1fdbf5db"}}"""
    )
    val legacyShowcaseMessage = read[MahjongCoreAdvanceRoundAPIMessage](
      """{"tableId":"table-be548ec5","request":{"playerId":"player-1fdbf5db","showcaseMode":true}}"""
    )
    val legacyNullActorMessage = read[MahjongCoreAdvanceRoundAPIMessage](
      """{"tableId":"table-be548ec5","request":{"playerId":null,"showcaseMode":false}}"""
    )
    val backendOptionMessage = read[MahjongCoreAdvanceRoundAPIMessage](
      """{"tableId":"table-be548ec5","request":[{"playerId":["player-1fdbf5db"]}]}"""
    )

    assertEquals(message.tableId, "table-be548ec5")
    assertEquals(message.request.flatMap(_.playerId), Some("player-1fdbf5db"))
    assertEquals(message.request.flatMap(_.showcaseMode), None)
    assertEquals(legacyShowcaseMessage.request.flatMap(_.showcaseMode), Some(true))
    assertEquals(legacyNullActorMessage.request.flatMap(_.playerId), None)
    assertEquals(legacyNullActorMessage.request.flatMap(_.showcaseMode), Some(false))
    assertEquals(backendOptionMessage.request.flatMap(_.playerId), Some("player-1fdbf5db"))
    assertEquals(backendOptionMessage.request.flatMap(_.showcaseMode), None)
  }

  test("showcase mode is stored as a backend process-wide flag") {
    MahjongCoreShowcaseMode.setEnabled(false)
    assertEquals(MahjongCoreShowcaseMode.enabled, false)

    val message = read[MahjongCoreSetShowcaseModeAPIMessage](
      """{"request":{"enabled":true}}"""
    )

    assertEquals(message.request, SetMahjongCoreShowcaseModeRequest(true))
    assertEquals(MahjongCoreShowcaseMode.setEnabled(message.request.enabled), true)
    assertEquals(MahjongCoreShowcaseMode.enabled, true)
    MahjongCoreShowcaseMode.setEnabled(false)
  }

  test("showcase mode deals the scripted default wall on east two") {
    val state = preparedRonState(MahjongRuleset(gameLength = MahjongGameLength.Tonpu, doubleRon = false), northCanRon = false)
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val roundEnded = finishRon(state, east.playerId, south.playerId)
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded, showcaseMode = true)
    val nextRound = advanced.currentRound.get

    assertEquals(nextRound.descriptor.roundWind, SeatWind.East)
    assertEquals(nextRound.descriptor.handNumber, 2)
    assertEquals(nextRound.doraIndicators.headOption, Some(PaifuTile("4z")))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.handTiles), Some(tiles("1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z")))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).flatMap(_.drawTile), Some(PaifuTile("0p")))
    assertEquals(advanced.seats.find(_.seat == SeatWind.South).map(_.handTiles), Some(tiles("1p", "1p", "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p", "9p", "9p")))
    assertEquals(advanced.seats.find(_.seat == SeatWind.West).map(_.handTiles), Some(tiles("1s", "1s", "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s", "9s", "9s")))
    assertEquals(advanced.seats.find(_.seat == SeatWind.North).map(_.handTiles), Some(tiles("1m", "1m", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "9m", "9m")))
    assertEquals(physicalTiles(advanced).size, 136)
  }

  test("one-kyoku table finishes after a completed hand and exposes final standings") {
    val state = preparedRonState(MahjongRuleset(gameLength = MahjongGameLength.OneKyoku, doubleRon = false), northCanRon = false)
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
    val finished = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)
    val finalStandings = finished.currentRound.toVector.flatMap(_.events).collectFirst {
      case MahjongEvent.TableFinished(_, standings) => standings
    }
      .getOrElse(Vector.empty)

    assertEquals(finished.status, MahjongTableStatus.Finished)
    assertEquals(finished.finishedRounds.size, 1)
    assert(finalStandings.nonEmpty)
    assertEquals(finalStandings.map(_.placement), Vector(1, 2, 3, 4))
    assertEquals(finalStandings.map(_.finalPoints), finalStandings.map(_.finalPoints).sortBy(-_))
  }

  test("dealer tsumo keeps the dealer, carries honba, and starts a fresh wall") {
    val state = preparedEastTsumoState(MahjongRuleset(gameLength = MahjongGameLength.Tonpu))
    val east = state.seats.find(_.seat == SeatWind.East).get

    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Tsumo)
    )
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)
    val nextRound = advanced.currentRound.get

    assertEquals(roundEnded.status, MahjongTableStatus.RoundEnded)
    assertEquals(roundEnded.currentRound.flatMap(_.result).flatMap(_.winner), Some(east.playerId))
    assertEquals(advanced.status, MahjongTableStatus.WaitingPlayerAction)
    assertEquals(nextRound.descriptor.handNumber, 1)
    assertEquals(nextRound.descriptor.honba, 1)
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.playerId), Some(east.playerId))
    assertEquals(nextRound.turnPlayerId, east.playerId)
    assertEquals(physicalTiles(advanced).size, 136)
  }

  test("exhaustive draw transfers noten payments and keeps east when east is tenpai") {
    val state = preparedExhaustiveDrawState()
    val east = state.seats.find(_.seat == SeatWind.East).get

    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("7z")))
    )
    val result = roundEnded.currentRound.flatMap(_.result).get
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(result.outcome, HandOutcome.ExhaustiveDraw)
    assertEquals(result.tenpaiPlayerIds.map(_.toSet), Some(Set(east.playerId)))
    assertEquals(result.scoreChanges.find(_.playerId == east.playerId).map(_.delta), Some(3000))
    assert(result.scoreChanges.filterNot(_.playerId == east.playerId).forall(_.delta == -1000))
    assertEquals(advanced.currentRound.map(_.descriptor.honba), Some(1))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.playerId), Some(east.playerId))
  }

  test("tonpu table finishes at east-four when dealer passes and target is reached") {
    val state = setRoundDescriptor(
      preparedRonState(
        MahjongRuleset(
          gameLength = MahjongGameLength.Tonpu,
          targetPoints = 25000,
          doubleRon = false
        ),
        northCanRon = false
      ),
      SeatWind.East,
      handNumber = 4
    )
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val roundEnded = finishRon(state, east.playerId, south.playerId)
    val finished = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(roundEnded.status, MahjongTableStatus.RoundEnded)
    assertEquals(finished.status, MahjongTableStatus.Finished)
    assertEquals(finished.finishedRounds.size, 1)
    assert(finished.currentRound.exists(_.events.collectFirst { case MahjongEvent.TableFinished(_, _) => () }.nonEmpty))
  }

  test("hanchan continues from east-four into south-one before south-round finish") {
    val state = setRoundDescriptor(
      preparedRonState(
        MahjongRuleset(
          gameLength = MahjongGameLength.Hanchan,
          targetPoints = 25000,
          doubleRon = false
        ),
        northCanRon = false
      ),
      SeatWind.East,
      handNumber = 4
    )
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val roundEnded = finishRon(state, east.playerId, south.playerId)
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(advanced.status, MahjongTableStatus.WaitingPlayerAction)
    assertEquals(advanced.currentRound.map(_.descriptor.roundWind), Some(SeatWind.South))
    assertEquals(advanced.currentRound.map(_.descriptor.handNumber), Some(1))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.playerId), Some(south.playerId))
  }

  test("hanchan table finishes at south-four when dealer passes and target is reached") {
    val state = setRoundDescriptor(
      preparedRonState(
        MahjongRuleset(
          gameLength = MahjongGameLength.Hanchan,
          targetPoints = 25000,
          doubleRon = false
        ),
        northCanRon = false
      ),
      SeatWind.South,
      handNumber = 4
    )
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val roundEnded = finishRon(state, east.playerId, south.playerId)
    val finished = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(finished.status, MahjongTableStatus.Finished)
    assertEquals(finished.finishedRounds.size, 1)
    assert(finished.currentRound.exists(_.events.collectFirst { case MahjongEvent.TableFinished(_, _) => () }.nonEmpty))
  }

  test("dealer win on the last scheduled tonpu hand extends the table with honba") {
    val state = setRoundDescriptor(
      preparedEastTsumoState(
        MahjongRuleset(
          gameLength = MahjongGameLength.Tonpu,
          targetPoints = 25000
        )
      ),
      SeatWind.East,
      handNumber = 4
    )
    val east = state.seats.find(_.seat == SeatWind.East).get
    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Tsumo)
    )
    val advanced = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(advanced.status, MahjongTableStatus.WaitingPlayerAction)
    assertEquals(advanced.currentRound.map(_.descriptor.roundWind), Some(SeatWind.East))
    assertEquals(advanced.currentRound.map(_.descriptor.handNumber), Some(4))
    assertEquals(advanced.currentRound.map(_.descriptor.honba), Some(1))
    assertEquals(advanced.seats.find(_.seat == SeatWind.East).map(_.playerId), Some(east.playerId))
  }

  test("dealer win on the last scheduled tonpu hand can finish when dealer is top") {
    val state = setRoundDescriptor(
      preparedEastTsumoState(
        MahjongRuleset(
          gameLength = MahjongGameLength.Tonpu,
          allLastDealerFinishAsTop = true
        )
      ),
      SeatWind.East,
      handNumber = 4
    )
    val east = state.seats.find(_.seat == SeatWind.East).get
    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Tsumo)
    )
    val finished = MahjongGameStateTransitionFunctions.advanceRound(roundEnded)

    assertEquals(finished.status, MahjongTableStatus.Finished)
    assertEquals(finished.finishedRounds.size, 1)
    assert(finished.currentRound.exists(_.events.collectFirst { case MahjongEvent.TableFinished(_, _) => () }.nonEmpty))
  }

  test("bankruptcy end switch controls whether a negative score finishes the table") {
    val bankruptcyOn = preparedRonState(
      MahjongRuleset(
        gameLength = MahjongGameLength.Hanchan,
        bankruptcyEnd = true,
        doubleRon = false
      ),
      northCanRon = false
    )
    val bankruptcyOff = preparedRonState(
      MahjongRuleset(
        gameLength = MahjongGameLength.Hanchan,
        bankruptcyEnd = false,
        doubleRon = false
      ),
      northCanRon = false
    )
    val lowEastPoints = 1000
    val endedWithBankruptcy = finishRon(withEastPoints(bankruptcyOn, lowEastPoints), bankruptcyOn.seats.find(_.seat == SeatWind.East).get.playerId, bankruptcyOn.seats.find(_.seat == SeatWind.South).get.playerId)
    val endedWithoutBankruptcy = finishRon(withEastPoints(bankruptcyOff, lowEastPoints), bankruptcyOff.seats.find(_.seat == SeatWind.East).get.playerId, bankruptcyOff.seats.find(_.seat == SeatWind.South).get.playerId)

    assert(endedWithBankruptcy.seats.exists(_.points < 0))
    assertEquals(MahjongGameStateTransitionFunctions.advanceRound(endedWithBankruptcy).status, MahjongTableStatus.Finished)
    assert(endedWithoutBankruptcy.seats.exists(_.points < 0))
    assertEquals(MahjongGameStateTransitionFunctions.advanceRound(endedWithoutBankruptcy).status, MahjongTableStatus.WaitingPlayerAction)
  }

  test("special rules switch red fives, open tanyao, min han, double ron and nagashi mangan") {
    assertEquals(fullWall(MahjongRuleset(akaDora = false)).count(isRed), 0)
    assertEquals(fullWall(MahjongRuleset(akaDora = true, akaDoraCount = 1)).filter(isRed), Vector(PaifuTile("0m")))
    assertEquals(fullWall(MahjongRuleset(akaDora = true, akaDoraCount = 2)).filter(isRed), Vector(PaifuTile("0m"), PaifuTile("0p")))
    assertEquals(fullWall(MahjongRuleset(akaDora = true, akaDoraCount = 99)).filter(isRed), Vector(PaifuTile("0m"), PaifuTile("0p"), PaifuTile("0s")))

    val openTanyao = analyzeOpenTanyao(MahjongRuleset(openTanyao = true))
    val closedByRule = analyzeOpenTanyao(MahjongRuleset(openTanyao = false))
    val blockedByMinHan = analyzeOpenTanyao(MahjongRuleset(openTanyao = true, minHan = 2))

    assert(openTanyao.exists(_.yaku.exists(_.kind == MahjongYakuKind.Tanyao)))
    assertEquals(closedByRule, None)
    assertEquals(blockedByMinHan, None)

    val doubleRonDisabled = preparedRonState(MahjongRuleset(doubleRon = false))
    val east = doubleRonDisabled.seats.find(_.seat == SeatWind.East).get
    val south = doubleRonDisabled.seats.find(_.seat == SeatWind.South).get
    val west = doubleRonDisabled.seats.find(_.seat == SeatWind.West).get
    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      doubleRonDisabled,
      MahjongSubmittedAction(east.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )

    assert(MahjongGameStateTransitionFunctions.legalActionsForPlayer(waitingRon, south.playerId).exists(_.commandType == MahjongCommandType.Ron))
    assert(!MahjongGameStateTransitionFunctions.legalActionsForPlayer(waitingRon, west.playerId).exists(_.commandType == MahjongCommandType.Ron))

    val nagashiDisabledState = preparedNagashiManganState().copy(ruleset = MahjongRuleset(nagashiMangan = false))
    val nagashiEast = nagashiDisabledState.seats.find(_.seat == SeatWind.East).get
    val (nagashiDisabledResultState, _) = MahjongGameStateTransitionFunctions.submitAction(
      nagashiDisabledState,
      MahjongSubmittedAction(nagashiEast.playerId, MahjongCommandType.Discard, tile = Some(PaifuTile("7z")))
    )
    val nagashiDisabledResult = nagashiDisabledResultState.currentRound.flatMap(_.result).get

    assertEquals(nagashiDisabledResult.outcome, HandOutcome.ExhaustiveDraw)
    assertEquals(nagashiDisabledResult.winner, None)
    assert(!nagashiDisabledResult.yaku.exists(_.kind == MahjongYakuKind.NagashiMangan))
  }

  test("deterministic long action chain preserves table invariants across calls, draws and round advances") {
    val initial = MahjongGameStateTransitionFunctions.startTable(
      TableId("mahjong-core-long-chain"),
      MahjongRuleset(
        gameLength = MahjongGameLength.Hanchan,
        targetPoints = 100000,
        bankruptcyEnd = false
      ),
      seed = "mahjong-core-long-chain-seed"
    )
    val (state, submittedActions) = playDeterministicActionChain(initial, targetSubmittedActions = 160)
    val playerView = MahjongGameStateTransitionFunctions.toView(state, Some(state.seats.head.playerId), includeLegalActions = true)
    val publicView = MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = true)

    assertEquals(submittedActions, 160)
    assert(state.currentRound.nonEmpty)
    assert(state.finishedRounds.nonEmpty)
    assertEquals(livePhysicalTiles(state).size, 136)
    assert(livePhysicalTiles(state).groupBy(indexOf).forall { case (_, copies) => copies.size == 4 })
    assertEquals(read[MahjongTableView](write(playerView)).version, playerView.version)
    assertEquals(read[MahjongTableView](write(publicView)).version, publicView.version)
    assert(publicView.seats.forall(_.handTiles.isEmpty))
  }

  test("waiting tile and helpful tile analysis respect waits and visible exhaustion") {
    val waits = MahjongHandAnalysisFunctions.waitingTiles(ronTenpaiHand).map(indexOf).toSet
    val helpful = MahjongHandAnalysisFunctions.helpfulTiles(
      tiles("1m", "1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "2z", "2z", "5z", "6z"),
      visibleTiles = tiles("9s", "9s", "9s")
    )

    assert(waits.contains(indexOf(PaifuTile("3m"))))
    assert(!waits.contains(indexOf(PaifuTile("7z"))))
    assert(helpful.values.forall(_ >= 1))
    assert(helpful.get(PaifuTile("9s")).exists(_ == 1))
  }

  test("tile functions preserve red fives and dora indicator wrapping") {
    assertEquals(normalize(PaifuTile("0m")), PaifuTile("0m"))
    assertEquals(redDoraCount(tiles("0m", "5m", "0p", "0s", "7z")), 3)
    assertEquals(tileOf(doraFromIndicator(indexOf(PaifuTile("9m")))), PaifuTile("1m"))
    assertEquals(tileOf(doraFromIndicator(indexOf(PaifuTile("4z")))), PaifuTile("1z"))
    assertEquals(tileOf(doraFromIndicator(indexOf(PaifuTile("7z")))), PaifuTile("5z"))
  }

  private def finishRon(
      state: MahjongTableState,
      discarder: PlayerId,
      winner: PlayerId
  ): MahjongTableState =
    val (waitingRon, _) = MahjongGameStateTransitionFunctions.submitAction(
      state,
      MahjongSubmittedAction(discarder, MahjongCommandType.Discard, tile = Some(PaifuTile("3m")))
    )
    val (roundEnded, _) = MahjongGameStateTransitionFunctions.submitAction(
      waitingRon,
      MahjongSubmittedAction(winner, MahjongCommandType.Ron)
    )
    roundEnded

  private def setRoundDescriptor(
      state: MahjongTableState,
      roundWind: SeatWind,
      handNumber: Int,
      honba: Int = 0
  ): MahjongTableState =
    state.copy(
      sticks = state.sticks.copy(honba = honba),
      currentRound = state.currentRound.map(round =>
        round.copy(
          descriptor = round.descriptor.copy(
            roundWind = roundWind,
            handNumber = handNumber,
            honba = honba
          ),
          roundStartSticks = round.roundStartSticks.copy(honba = honba)
        )
      )
    )

  private def withEastPoints(state: MahjongTableState, points: Int): MahjongTableState =
    state.copy(
      seats = state.seats.map(seat =>
        if seat.seat == SeatWind.East then seat.copy(points = points)
        else seat
      )
    )

  private def analyzeOpenTanyao(ruleset: MahjongRuleset) =
    val winner = PlayerId("open-tanyao-winner")
    val target = PlayerId("open-tanyao-target")
    val seatByPlayer = Map(
      target -> SeatWind.East,
      winner -> SeatWind.South,
      PlayerId("open-tanyao-west") -> SeatWind.West,
      PlayerId("open-tanyao-north") -> SeatWind.North
    )
    val openChi = MahjongMeld(
      meldType = MahjongMeldType.Chi,
      owner = winner,
      fromPlayer = Some(target),
      calledTile = Some(PaifuTile("3m")),
      tiles = tiles("2m", "3m", "4m"),
      closed = false
    )

    MahjongYakuAnalysisFunctions.analyzeWin(
      MahjongWinContext(
        winner = winner,
        target = Some(target),
        seatByPlayer = seatByPlayer,
        roundWind = SeatWind.East,
        handTiles = tiles("2p", "3p", "4p", "3s", "4s", "6s", "7s", "8s", "6p", "6p"),
        melds = Vector(openChi),
        winningTile = PaifuTile("5s"),
        doraIndicators = Vector(PaifuTile("1z")),
        ruleset = ruleset
      )
    )

  private def playDeterministicActionChain(
      initial: MahjongTableState,
      targetSubmittedActions: Int
  ): (MahjongTableState, Int) =
    var state = initial
    var submittedActions = 0
    while submittedActions < targetSubmittedActions do
      if state.status == MahjongTableStatus.RoundEnded then
        state = MahjongGameStateTransitionFunctions.advanceRound(state)
      else
        val (playerId, action) = chooseDeterministicAction(state)
        val (nextState, _) = MahjongGameStateTransitionFunctions.submitAction(
          state,
          MahjongSubmittedAction(
            playerId = playerId,
            commandType = action.commandType,
            tile = action.tile,
            tiles = action.tiles,
            targetSequenceNo = action.targetSequenceNo
          )
        )
        state = nextState
        submittedActions += 1

      if submittedActions % 20 == 0 then
        assertEquals(livePhysicalTiles(state).size, 136)
        read[MahjongTableView](write(MahjongGameStateTransitionFunctions.toView(state, Some(state.seats.head.playerId), includeLegalActions = true)))

    (state, submittedActions)

  private def chooseDeterministicAction(state: MahjongTableState): (PlayerId, MahjongLegalAction) =
    state.currentRound match
      case Some(round) if round.phase == MahjongRoundPhase.PlayerTurn =>
        val playerId = round.turnPlayerId
        val actions = MahjongGameStateTransitionFunctions.legalActionsForPlayer(state, playerId)
        val action =
          actions.find(_.commandType == MahjongCommandType.ClosedKan)
            .orElse(actions.find(_.commandType == MahjongCommandType.AddedKan))
            .orElse(actions.filter(_.commandType == MahjongCommandType.Discard).sortBy(action => action.tile.map(_.value).getOrElse("")).lastOption)
            .orElse(actions.find(_.commandType == MahjongCommandType.Riichi))
            .orElse(actions.find(_.commandType == MahjongCommandType.Tsumo))
            .getOrElse(throw IllegalStateException(s"No deterministic player-turn action for ${playerId.value}"))
        playerId -> action

      case Some(round) if round.phase == MahjongRoundPhase.CallDecision =>
        val candidates = state.seats
          .map(_.playerId)
          .flatMap(playerId =>
            MahjongGameStateTransitionFunctions
              .legalActionsForPlayer(state, playerId)
              .map(action => playerId -> action)
          )
        candidates
          .find((_, action) => action.commandType == MahjongCommandType.Chi)
          .orElse(candidates.find((_, action) => action.commandType == MahjongCommandType.Pon))
          .orElse(candidates.find((_, action) => action.commandType == MahjongCommandType.OpenKan))
          .orElse(candidates.find((_, action) => action.commandType == MahjongCommandType.Pass))
          .orElse(candidates.find((_, action) => action.commandType == MahjongCommandType.Ron))
          .getOrElse(throw IllegalStateException("CallDecision without deterministic candidate action"))

      case other =>
        throw IllegalStateException(s"Unsupported state in deterministic action chain: ${state.status}/${other.map(_.phase)}")

  private def livePhysicalTiles(state: MahjongTableState): Vector[PaifuTile] =
    val roundTiles = state.currentRound.toVector.flatMap { round =>
      val usedReplacementCount = math.max(0, round.doraIndicators.size - 1)
      round.wall ++ round.deadWall.drop(usedReplacementCount) ++ round.uraDoraIndicators
    }
    val seatTiles = state.seats.flatMap { seat =>
      seat.handTiles ++
        seat.drawTile.toVector ++
        seat.melds.flatMap(_.tiles) ++
        seat.river.filter(_.calledBy.isEmpty).map(_.tile)
    }

    roundTiles ++ seatTiles

  private def preparedRonState(ruleset: MahjongRuleset, northCanRon: Boolean = true) =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-ron-test"), ruleset, seed = "mahjong-core-ron-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val quietHand = tiles("2m", "2m", "4m", "5m", "6m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "6z")
    val seats = state.seats.map { seat =>
      seat.seat match
        case SeatWind.East =>
          seat.copy(handTiles = quietHand, drawTile = Some(PaifuTile("3m")), riichi = false, ippatsu = false, furiten = false)
        case SeatWind.South | SeatWind.West =>
          seat.copy(handTiles = ronTenpaiHand, drawTile = None, riichi = true, ippatsu = false, furiten = false)
        case SeatWind.North if northCanRon =>
          seat.copy(handTiles = ronTenpaiHand, drawTile = None, riichi = true, ippatsu = false, furiten = false)
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

  private def preparedChiPonState() =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-chi-pon-test"), MahjongRuleset(), seed = "mahjong-core-chi-pon-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val southCallHand = tiles("2m", "3m", "3m", "4m", "5p", "6p", "7p", "2s", "3s", "4s", "5z", "6z", "7z")
    val quietHand = tiles("2m", "2m", "4m", "5m", "6m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "6z")
    state.copy(
      seats = state.seats.map {
        case seat if seat.seat == SeatWind.East =>
          seat.copy(handTiles = quietHand, drawTile = Some(PaifuTile("3m")), riichi = false, ippatsu = false, furiten = false)
        case seat if seat.seat == SeatWind.South =>
          seat.copy(handTiles = southCallHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
        case seat =>
          seat.copy(handTiles = quietHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
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

  private def preparedSingleRiichiRonState() =
    val state = preparedRonState(MahjongRuleset(doubleRon = false), northCanRon = false)
    val quietHand = tiles("2m", "2m", "4m", "5m", "6m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "6z")
    state.copy(
      seats = state.seats.map {
        case seat if seat.seat == SeatWind.South =>
          seat.copy(handTiles = ronTenpaiHand, drawTile = None, riichi = true, ippatsu = false, furiten = false)
        case seat if seat.seat == SeatWind.West || seat.seat == SeatWind.North =>
          seat.copy(handTiles = quietHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
        case seat => seat
      },
      currentRound = state.currentRound.map(round =>
        round.copy(
          wall = tiles("5z", "3m", "6z")
        )
      )
    )

  private def preparedRiichiClosedKanState(drawTile: String) =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId(s"mahjong-core-riichi-kan-$drawTile"), MahjongRuleset(), seed = s"mahjong-core-riichi-kan-$drawTile-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val kanHand =
      if drawTile == "1m" then
        tiles("1m", "1m", "1m", "1p", "2p", "3p", "1s", "2s", "3s", "4m", "5m", "7z", "7z")
      else
        tiles("1m", "1m", "1m", "1m", "1p", "2p", "3p", "1s", "2s", "3s", "4m", "5m", "7z")
    val quietHand = tiles("2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "5z", "6z", "6z")
    state.copy(
      seats = state.seats.map { seat =>
        if seat.seat == SeatWind.East then
          seat.copy(handTiles = kanHand, drawTile = Some(PaifuTile(drawTile)), riichi = true, ippatsu = false, furiten = false)
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

  private def ronTenpaiHand: Vector[PaifuTile] =
    tiles("1m", "2m", "4m", "5m", "6m", "1p", "2p", "3p", "1s", "2s", "3s", "7z", "7z")

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

  private def preparedEastTsumoState(ruleset: MahjongRuleset) =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-east-tsumo-test"), ruleset, seed = "mahjong-core-east-tsumo-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val quietHand = tiles("2m", "2m", "4m", "5m", "6m", "2p", "3p", "4p", "2s", "3s", "4s", "5z", "6z")
    state.copy(
      seats = state.seats.map { seat =>
        if seat.seat == SeatWind.East then
          seat.copy(handTiles = ronTenpaiHand, drawTile = Some(PaifuTile("3m")), riichi = true, ippatsu = false, furiten = false)
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

  private def preparedExhaustiveDrawState() =
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-core-exhaustive-draw-test"), MahjongRuleset(nagashiMangan = false), seed = "mahjong-core-exhaustive-draw-test-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val notenHand = tiles("1m", "1m", "1m", "2m", "2m", "3m", "4p", "6p", "8p", "1s", "4s", "7s", "5z")
    state.copy(
      seats = state.seats.map { seat =>
        if seat.seat == SeatWind.East then
          seat.copy(handTiles = ronTenpaiHand, drawTile = Some(PaifuTile("7z")), riichi = false, ippatsu = false, furiten = false)
        else seat.copy(handTiles = notenHand, drawTile = None, riichi = false, ippatsu = false, furiten = false)
      },
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

  private def physicalTiles(state: MahjongTableState): Vector[PaifuTile] =
    val round = state.currentRound.get
    state.seats.flatMap(seat => seat.handTiles ++ seat.drawTile.toVector) ++
      round.wall ++
      round.deadWall ++
      round.uraDoraIndicators

  private def scoreDelta(result: AgariResult, playerId: PlayerId): Int =
    result.scoreChanges.find(_.playerId == playerId).map(_.delta).getOrElse(0)

  private def tiles(values: String*): Vector[PaifuTile] =
    values.toVector.map(PaifuTile(_))
