package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 表示一张弃牌的公开数据；河牌不含暗牌信息，前后端共用同一类型。 */
final case class MahjongDiscard(
    sequenceNo: Int,
    playerId: PlayerId,
    tile: PaifuTile,
    tsumogiri: Boolean = false,
    riichiDeclared: Boolean = false,
    calledBy: Option[PlayerId] = None
)

object MahjongDiscard:
  given ReadWriter[MahjongDiscard] = macroRW
