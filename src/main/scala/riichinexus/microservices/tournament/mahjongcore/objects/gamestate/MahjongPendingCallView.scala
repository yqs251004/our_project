package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端可见的鸣牌/荣和等待状态，只给可响应的 viewer 返回，不暴露其他候选玩家。 */
final case class MahjongPendingCallView(
    discardSequenceNo: Int,
    discardPlayerId: PlayerId,
    tile: PaifuTile,
    waitingPlayerIds: Vector[PlayerId]
)

object MahjongPendingCallView:
  given ReadWriter[MahjongPendingCallView] = macroRW
