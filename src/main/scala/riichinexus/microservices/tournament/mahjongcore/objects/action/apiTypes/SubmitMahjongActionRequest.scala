package riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongCommandType
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 玩家提交实时麻将行动的请求，idempotencyKey 用于避免重复点击造成重复落子。 */
final case class SubmitMahjongActionRequest(
    playerId: String,
    commandType: MahjongCommandType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    targetSequenceNo: Option[Int] = None,
    idempotencyKey: String
)

object SubmitMahjongActionRequest:
  given ReadWriter[SubmitMahjongActionRequest] = macroRW
