package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** PaifuAction 表示前后端共享的牌谱动作 数据结构，包含sequenceNo、actor、actionType、tile、shantenAfterAction、handTilesAfterAction等。 */

final case class PaifuAction(
    sequenceNo: Int,
    actor: Option[PlayerId] = None,
    actionType: PaifuActionType,
    tile: Option[PaifuTile] = None,
    shantenAfterAction: Option[Int] = None,
    handTilesAfterAction: Option[Vector[PaifuTile]] = None,
    revealedTiles: Vector[PaifuTile] = Vector.empty,
    note: Option[String] = None,
    fromPlayer: Option[PlayerId] = None,
    targetSequenceNo: Option[Int] = None
)
