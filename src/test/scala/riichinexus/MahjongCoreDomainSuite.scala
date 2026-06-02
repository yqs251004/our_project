package riichinexus

import munit.FunSuite

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions.MahjongYakuAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model.MahjongWinContext
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongCommandType
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
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
