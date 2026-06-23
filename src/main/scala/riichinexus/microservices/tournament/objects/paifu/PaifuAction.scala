package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.PlayerId

/** 牌谱时间线中的单个可回放动作。
  *
  * 动作记录序号、操作者、动作类型、相关牌、动作后向听/手牌快照、可见牌和来源玩家，可支撑回放、统计和动画同步。
  */
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
