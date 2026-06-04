package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 前端可见的座位状态；手牌会按观看者权限裁剪，本人可见，旁观者只看张数。 */
final case class MahjongSeatView(
    seat: SeatWind,
    playerId: PlayerId,
    points: Int,
    isDealer: Boolean,
    handTiles: Option[Vector[PaifuTile]] = None,
    handTileCount: Int,
    melds: Vector[MahjongMeld] = Vector.empty,
    river: Vector[MahjongDiscard] = Vector.empty,
    riichi: Boolean = false,
    ippatsu: Boolean = false,
    furiten: Boolean = false,
    tenpai: Option[Boolean] = None
)

object MahjongSeatView:
  given ReadWriter[MahjongSeatView] = macroRW
