package riichinexus.microservices.tournament.mahjongcore.tables.tablestate

import munit.FunSuite

import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongCallCandidate, MahjongPendingCallState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongCommandType, MahjongLegalAction}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongMeldType, MahjongRoundPhase, MahjongRuleset}
import riichinexus.microservices.tournament.objects.paifumanagement.{AgariResult, FinalStanding, HandOutcome, PaifuTile, RoundSettlement, RoundSettlementNote, ScoreChange}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}
import upickle.default.{read, write}

class MahjongTableStateJsonCodecSuite extends FunSuite:

import riichinexus.system.json.MahjongTableStateJsonCodecs.given

  test("table state json codec round-trips all internal event variants and pending call state") {
    val state = MahjongGameStateTransitionFunctions.startTable(TableId("mahjong-state-codec"), MahjongRuleset(), seed = "mahjong-state-codec-seed")
    val east = state.seats.find(_.seat == SeatWind.East).get
    val south = state.seats.find(_.seat == SeatWind.South).get
    val descriptor = state.currentRound.get.descriptor
    val pon = MahjongMeld(
      meldType = MahjongMeldType.Pon,
      owner = south.playerId,
      fromPlayer = Some(east.playerId),
      calledTile = Some(PaifuTile("3m")),
      tiles = Vector.fill(3)(PaifuTile("3m")),
      closed = false
    )
    val closedKan = MahjongMeld(
      meldType = MahjongMeldType.ClosedKan,
      owner = east.playerId,
      tiles = Vector.fill(4)(PaifuTile("1z")),
      closed = true
    )
    val result = AgariResult(
      outcome = HandOutcome.Ron,
      winner = Some(south.playerId),
      target = Some(east.playerId),
      yaku = Vector.empty,
      points = 8000,
      scoreChanges = Vector(
        ScoreChange(east.playerId, -8000),
        ScoreChange(south.playerId, 8000),
        ScoreChange(state.seats.find(_.seat == SeatWind.West).get.playerId, 0),
        ScoreChange(state.seats.find(_.seat == SeatWind.North).get.playerId, 0)
      ),
      settlement = Some(RoundSettlement(notes = Vector(RoundSettlementNote.DoubleRon)))
    )
    val standings = state.seats.zipWithIndex.map { case (seat, index) =>
      FinalStanding(seat.playerId, seat.seat, seat.points, placement = index + 1)
    }
    val events = Vector(
      MahjongEvent.TableStarted(1),
      MahjongEvent.RoundStarted(2, descriptor),
      MahjongEvent.TileDrawn(3, east.playerId, PaifuTile("5m")),
      MahjongEvent.TileDiscarded(4, east.playerId, PaifuTile("3m"), tsumogiri = false),
      MahjongEvent.MeldCalled(5, south.playerId, pon),
      MahjongEvent.RiichiDeclared(6, east.playerId, PaifuTile("7z")),
      MahjongEvent.KanDeclared(7, east.playerId, closedKan),
      MahjongEvent.DoraRevealed(8, PaifuTile("4p")),
      MahjongEvent.WinDeclared(9, south.playerId, Some(east.playerId), PaifuTile("3m")),
      MahjongEvent.PlayerPassed(10, state.seats.find(_.seat == SeatWind.West).get.playerId),
      MahjongEvent.RoundFinished(11, result),
      MahjongEvent.TableFinished(12, standings)
    )
    val pending = MahjongPendingCallState(
      discardSequenceNo = 4,
      discardPlayerId = east.playerId,
      tile = PaifuTile("3m"),
      candidates = Vector(
        MahjongCallCandidate(
          south.playerId,
          Vector(
            MahjongLegalAction(
              commandType = MahjongCommandType.Pon,
              tile = Some(PaifuTile("3m")),
              tiles = Vector.fill(3)(PaifuTile("3m")),
              fromPlayerId = Some(east.playerId),
              targetSequenceNo = Some(4),
              priority = 60
            )
          )
        )
      ),
      acceptedRonPlayerIds = Vector(south.playerId)
    )
    val enriched = state.copy(
      currentRound = state.currentRound.map(_.copy(
        phase = MahjongRoundPhase.CallDecision,
        pendingCall = Some(pending),
        events = events,
        result = Some(result)
      ))
    )

    val decoded = read[MahjongTableState](write(enriched))
    val decodedRound = decoded.currentRound.get

    assertEquals(decoded.tableId, enriched.tableId)
    assertEquals(decoded.version, enriched.version)
    assertEquals(decodedRound.phase, MahjongRoundPhase.CallDecision)
    assertEquals(decodedRound.pendingCall, Some(pending))
    assertEquals(decodedRound.result, Some(result))
    assertEquals(decodedRound.events, events)
  }
