package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** MahjongHandDecomposition 表示标准手牌拆解结果，包含面子列表和雀头牌索引。 */
final case class MahjongHandDecomposition(
    melds: Vector[MahjongHandMeld],
    pairIndex: Int
)
