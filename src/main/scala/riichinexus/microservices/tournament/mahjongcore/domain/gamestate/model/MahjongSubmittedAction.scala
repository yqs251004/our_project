package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongCommandType
import riichinexus.microservices.tournament.objects.paifu.PaifuTile

import riichinexus.system.json.JsonCodecs.given
/** MahjongSubmittedAction 表示后端领域中的麻将Submitted动作状态或规则，包含玩家 ID、commandType、tile、tiles、targetSequenceNo。 */
final case class MahjongSubmittedAction(
    playerId: PlayerId,
    commandType: MahjongCommandType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    targetSequenceNo: Option[Int] = None
)