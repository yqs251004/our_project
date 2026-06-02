package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 前端可见的鸣牌/荣和等待状态，是 MahjongPendingCallState 裁掉具体候选行动后的公开投影。 */
final case class MahjongPendingCallView(
    discardSequenceNo: Int,
    discardPlayerId: PlayerId,
    tile: PaifuTile,
    waitingPlayerIds: Vector[PlayerId]
)

object MahjongPendingCallView:
  given ReadWriter[MahjongPendingCallView] = macroRW
