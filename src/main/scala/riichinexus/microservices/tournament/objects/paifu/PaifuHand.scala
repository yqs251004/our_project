package riichinexus.microservices.tournament.objects.paifu

/** PaifuHand 表示前后端共享的牌谱手牌 数据结构，包含tiles。 */

final case class PaifuHand(
    tiles: Vector[PaifuTile]
)
