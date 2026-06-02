package riichinexus.microservices.tournament.mahjongcore.objects.action

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.{PaifuActionType, PaifuTile}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 对外公开的已发生事件摘要，事件类型复用牌谱侧 PaifuActionType，便于最终生成回放。 */
final case class MahjongPublicEventView(
    sequenceNo: Int,
    actor: Option[PlayerId],
    actionType: PaifuActionType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    note: Option[String] = None
)

object MahjongPublicEventView:
  given ReadWriter[MahjongPublicEventView] = macroRW
