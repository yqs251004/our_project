package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** 标准手牌拆解中的一个面子。
  *
  * `tileIndex` 使用手牌分析内部的规范化牌索引，`concealed` 标记该面子是否暗面子，供暗刻、门清和形状役判断。
  */
final case class MahjongHandMeld(
    meldType: MahjongHandMeldType,
    tileIndex: Int,
    concealed: Boolean
)
