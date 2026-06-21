package riichinexus.microservices.tournament.objects.paifu

/** 牌谱回放中某一时刻的手牌快照。
  *
  * 手牌只记录闭合手牌的牌面序列，副露、河牌和和牌展示由其他牌谱结构表达。
  */
final case class PaifuHand(
    tiles: Vector[PaifuTile]
)
