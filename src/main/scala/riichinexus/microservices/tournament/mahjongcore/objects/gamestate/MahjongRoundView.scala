package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, KyokuDescriptor, PaifuTile}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端可见的当前小局摘要，隐藏牌山和完整暗牌，只暴露桌面展示需要的信息。 */
final case class MahjongRoundView(
    descriptor: KyokuDescriptor,
    phase: MahjongRoundPhase,
    turnPlayerId: Option[PlayerId],
    wallTileCount: Int,
    sticks: MahjongTableSticks,
    doraIndicators: Vector[PaifuTile],
    doraIndicatorVisibleCount: Int,
    pendingCall: Option[MahjongPendingCallView] = None,
    result: Option[AgariResult] = None
)

object MahjongRoundView:
  given ReadWriter[MahjongRoundView] = macroRW
