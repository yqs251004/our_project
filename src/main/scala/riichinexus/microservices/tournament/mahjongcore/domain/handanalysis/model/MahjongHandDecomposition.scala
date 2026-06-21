package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** 一副标准和牌手牌的完整拆解。
  *
  * 拆解结果包含四个面子和一组雀头的规范化索引，供役种分析判断形状役与门清相关役。
  */
final case class MahjongHandDecomposition(
    melds: Vector[MahjongHandMeld],
    pairIndex: Int
)
