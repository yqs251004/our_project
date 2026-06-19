package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** MahjongHandMeld 表示手牌拆解中的一个面子，记录类型、牌索引和是否暗面子。 */
final case class MahjongHandMeld(
    meldType: MahjongHandMeldType,
    tileIndex: Int,
    concealed: Boolean
)
