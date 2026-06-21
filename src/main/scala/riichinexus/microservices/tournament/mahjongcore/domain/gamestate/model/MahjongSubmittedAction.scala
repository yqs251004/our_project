package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongCommandType
import riichinexus.microservices.tournament.objects.paifu.PaifuTile

import riichinexus.system.json.JsonCodecs.given

/** API 请求进入领域规则层后的玩家动作。
  *
  * 它已经解析出玩家 ID、命令类型、相关牌和目标事件序号，后续由状态机校验合法性并写入内部事件流。
  */
final case class MahjongSubmittedAction(
    playerId: PlayerId,
    commandType: MahjongCommandType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    targetSequenceNo: Option[Int] = None
)
