package riichinexus.microservices.tournament.objects.paifumanagement

/** PaifuHand 表示前后端共享的牌谱手牌 数据结构，包含tiles。 */

final case class PaifuHand(
    tiles: Vector[PaifuTile]
)
