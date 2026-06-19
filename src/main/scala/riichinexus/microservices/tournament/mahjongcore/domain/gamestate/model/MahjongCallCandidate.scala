package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongLegalAction

import riichinexus.system.json.JsonCodecs.given
/** 后端内部的单个玩家响应候选，列出该玩家针对当前弃牌可执行的行动。 */
final case class MahjongCallCandidate(
    playerId: PlayerId,
    legalActions: Vector[MahjongLegalAction]
)