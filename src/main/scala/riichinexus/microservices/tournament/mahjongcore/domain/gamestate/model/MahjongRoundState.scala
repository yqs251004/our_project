package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRoundPhase, MahjongTableSticks}
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, KyokuDescriptor, PaifuTile}

import riichinexus.system.json.JsonCodecs.given
/** 后端内部的当前小局状态，包含牌山、死牌、宝牌、待响应鸣牌和完整事件流。 */
final case class MahjongRoundState(
    descriptor: KyokuDescriptor,
    phase: MahjongRoundPhase,
    roundStartSticks: MahjongTableSticks,
    wall: Vector[PaifuTile],
    deadWall: Vector[PaifuTile],
    doraIndicators: Vector[PaifuTile],
    uraDoraIndicators: Vector[PaifuTile],
    initialHands: Map[PlayerId, Vector[PaifuTile]],
    turnPlayerId: PlayerId,
    pendingCall: Option[MahjongPendingCallState],
    events: Vector[MahjongEvent],
    result: Option[AgariResult]
)