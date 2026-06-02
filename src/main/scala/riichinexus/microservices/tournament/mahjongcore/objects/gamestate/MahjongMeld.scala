package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 表示一次副露或杠子的公开数据；副露本身是桌面公开信息，前后端共用同一类型。 */
final case class MahjongMeld(
    meldType: MahjongMeldType,
    owner: PlayerId,
    fromPlayer: Option[PlayerId] = None,
    calledTile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    closed: Boolean = false
)

object MahjongMeld:
  given ReadWriter[MahjongMeld] = macroRW
