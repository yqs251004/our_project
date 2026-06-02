package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongCommandType
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile

final case class MahjongSubmittedAction(
    playerId: PlayerId,
    commandType: MahjongCommandType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    targetSequenceNo: Option[Int] = None
)
