package riichinexus.system.json

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongCallCandidate, MahjongCallResponse, MahjongPendingCallState, MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongMeld
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, FinalStanding, KyokuDescriptor, PaifuTile}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW, read, readwriter, writeJs}

object MahjongTableStateJsonCodecs:
  given ReadWriter[MahjongEvent] =
    readwriter[ujson.Value].bimap[MahjongEvent](writeEvent, readEvent)

  given ReadWriter[MahjongCallCandidate] = macroRW
  given ReadWriter[MahjongCallResponse] = macroRW
  given ReadWriter[MahjongPendingCallState] = macroRW
  given ReadWriter[MahjongSeatState] = macroRW
  given ReadWriter[MahjongRoundState] = macroRW
  given ReadWriter[MahjongTableState] = macroRW

  private def writeEvent(event: MahjongEvent): ujson.Value =
    event match
      case MahjongEvent.TableStarted(sequenceNo) =>
        eventObj("TableStarted", sequenceNo)
      case MahjongEvent.RoundStarted(sequenceNo, descriptor) =>
        eventObj("RoundStarted", sequenceNo, "descriptor" -> writeJs(descriptor))
      case MahjongEvent.TileDrawn(sequenceNo, playerId, tile) =>
        eventObj("TileDrawn", sequenceNo, "playerId" -> writeJs(playerId), "tile" -> writeJs(tile))
      case MahjongEvent.TileDiscarded(sequenceNo, playerId, tile, tsumogiri) =>
        eventObj("TileDiscarded", sequenceNo, "playerId" -> writeJs(playerId), "tile" -> writeJs(tile), "tsumogiri" -> writeJs(tsumogiri))
      case MahjongEvent.MeldCalled(sequenceNo, playerId, meld) =>
        eventObj("MeldCalled", sequenceNo, "playerId" -> writeJs(playerId), "meld" -> writeJs(meld))
      case MahjongEvent.RiichiDeclared(sequenceNo, playerId, tile) =>
        eventObj("RiichiDeclared", sequenceNo, "playerId" -> writeJs(playerId), "tile" -> writeJs(tile))
      case MahjongEvent.KanDeclared(sequenceNo, playerId, meld) =>
        eventObj("KanDeclared", sequenceNo, "playerId" -> writeJs(playerId), "meld" -> writeJs(meld))
      case MahjongEvent.DoraRevealed(sequenceNo, tile) =>
        eventObj("DoraRevealed", sequenceNo, "tile" -> writeJs(tile))
      case MahjongEvent.WinDeclared(sequenceNo, winner, target, tile) =>
        eventObj("WinDeclared", sequenceNo, "winner" -> writeJs(winner), "target" -> writeJs(target), "tile" -> writeJs(tile))
      case MahjongEvent.PlayerPassed(sequenceNo, playerId) =>
        eventObj("PlayerPassed", sequenceNo, "playerId" -> writeJs(playerId))
      case MahjongEvent.RoundFinished(sequenceNo, result) =>
        eventObj("RoundFinished", sequenceNo, "result" -> writeJs(result))
      case MahjongEvent.TableFinished(sequenceNo, finalStandings) =>
        eventObj("TableFinished", sequenceNo, "finalStandings" -> writeJs(finalStandings))

  private def readEvent(json: ujson.Value): MahjongEvent =
    val obj = json.obj
    val sequenceNo = read[Int](obj("sequenceNo"))
    obj("type").str match
      case "TableStarted" => MahjongEvent.TableStarted(sequenceNo)
      case "RoundStarted" => MahjongEvent.RoundStarted(sequenceNo, read[KyokuDescriptor](obj("descriptor")))
      case "TileDrawn" => MahjongEvent.TileDrawn(sequenceNo, read[PlayerId](obj("playerId")), read[PaifuTile](obj("tile")))
      case "TileDiscarded" => MahjongEvent.TileDiscarded(sequenceNo, read[PlayerId](obj("playerId")), read[PaifuTile](obj("tile")), read[Boolean](obj("tsumogiri")))
      case "MeldCalled" => MahjongEvent.MeldCalled(sequenceNo, read[PlayerId](obj("playerId")), read[MahjongMeld](obj("meld")))
      case "RiichiDeclared" => MahjongEvent.RiichiDeclared(sequenceNo, read[PlayerId](obj("playerId")), read[PaifuTile](obj("tile")))
      case "KanDeclared" => MahjongEvent.KanDeclared(sequenceNo, read[PlayerId](obj("playerId")), read[MahjongMeld](obj("meld")))
      case "DoraRevealed" => MahjongEvent.DoraRevealed(sequenceNo, read[PaifuTile](obj("tile")))
      case "WinDeclared" => MahjongEvent.WinDeclared(sequenceNo, read[PlayerId](obj("winner")), read[Option[PlayerId]](obj("target")), read[PaifuTile](obj("tile")))
      case "PlayerPassed" => MahjongEvent.PlayerPassed(sequenceNo, read[PlayerId](obj("playerId")))
      case "RoundFinished" => MahjongEvent.RoundFinished(sequenceNo, read[AgariResult](obj("result")))
      case "TableFinished" => MahjongEvent.TableFinished(sequenceNo, read[Vector[FinalStanding]](obj("finalStandings")))
      case other => throw IllegalArgumentException(s"Unsupported MahjongEvent type: $other")

  private def eventObj(eventType: String, sequenceNo: Int, fields: (String, ujson.Value)*): ujson.Obj =
    val obj = ujson.Obj("type" -> ujson.Str(eventType), "sequenceNo" -> writeJs(sequenceNo))
    fields.foreach { case (key, value) => obj(key) = value }
    obj
