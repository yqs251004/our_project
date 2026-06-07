package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongLegalAction

final case class MahjongCallResponse(
    playerId: PlayerId,
    action: MahjongLegalAction
)
