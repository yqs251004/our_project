package riichinexus

import munit.FunSuite

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongCallCandidate, MahjongPendingCallState}
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRoundPhase, MahjongRuleset, MahjongTableStatus}
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
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

    assert(result.exists(_.yaku.exists(_.name.startsWith("国士无双"))))
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
