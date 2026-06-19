package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongLegalAction

import riichinexus.system.json.JsonCodecs.given
/** MahjongCallResponse 表示后端领域中的麻将鸣牌响应状态或规则，包含玩家 ID、action。 */
final case class MahjongCallResponse(
    playerId: PlayerId,
    action: MahjongLegalAction
)