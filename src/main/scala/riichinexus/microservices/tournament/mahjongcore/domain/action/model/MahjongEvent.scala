package riichinexus.microservices.tournament.mahjongcore.domain.action.model

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongMeld
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, FinalStanding, KyokuDescriptor, PaifuTile}

/** 后端内部事件流，记录实时对局已经发生的事实，并在归档时转换成 PaifuAction。 */
enum MahjongEvent:
  case TableStarted(sequenceNo: Int)
  case RoundStarted(sequenceNo: Int, descriptor: KyokuDescriptor)
  case TileDrawn(sequenceNo: Int, playerId: PlayerId, tile: PaifuTile)
  case TileDiscarded(sequenceNo: Int, playerId: PlayerId, tile: PaifuTile, tsumogiri: Boolean)
  case MeldCalled(sequenceNo: Int, playerId: PlayerId, meld: MahjongMeld)
  case RiichiDeclared(sequenceNo: Int, playerId: PlayerId, tile: PaifuTile)
  case KanDeclared(sequenceNo: Int, playerId: PlayerId, meld: MahjongMeld)
  case DoraRevealed(sequenceNo: Int, tile: PaifuTile)
  case WinDeclared(sequenceNo: Int, winner: PlayerId, target: Option[PlayerId], tile: PaifuTile)
  case PlayerPassed(sequenceNo: Int, playerId: PlayerId)
  case RoundFinished(sequenceNo: Int, result: AgariResult)
  case TableFinished(sequenceNo: Int, finalStandings: Vector[FinalStanding])
