package riichinexus

import munit.FunSuite
import upickle.default.writeJs

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given

class PaifuJsonContractSuite extends FunSuite:

  test("paifu json writers keep frontend-required default fields") {
    val seatJson = writeJs(
      TableSeat(
        seat = SeatWind.East,
        playerId = PlayerId("player-east"),
        ready = true
      )
    )
    val descriptorJson = writeJs(KyokuDescriptor(SeatWind.East, 1))
    val actionJson = writeJs(
      PaifuAction(
        sequenceNo = 1,
        actionType = PaifuActionType.Draw
      )
    )
    val settlementJson = writeJs(RoundSettlement(notes = Vector("checked")))

    assertEquals(seatJson.obj("initialPoints").num.toInt, 25000)
    assertEquals(seatJson.obj("disconnected").bool, false)
    assertEquals(seatJson.obj("ready").bool, true)

    assertEquals(descriptorJson.obj("honba").num.toInt, 0)

    assert(actionJson.obj.contains("revealedTiles"))
    assertEquals(actionJson.obj("revealedTiles").arr.toVector, Vector.empty)

    assertEquals(settlementJson.obj("riichiSticksDelta").num.toInt, 0)
    assertEquals(settlementJson.obj("honbaPayment").num.toInt, 0)
    assertEquals(settlementJson.obj("notes").arr.map(_.str).toVector, Vector("checked"))
  }
