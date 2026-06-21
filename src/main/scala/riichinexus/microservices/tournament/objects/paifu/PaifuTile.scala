package riichinexus.microservices.tournament.objects.paifu

/** 牌谱中一张可渲染麻将牌。
  *
  * `rank` 保存同花色内的点数或字牌序号，`suit` 保存花色；是否红宝牌等扩展信息由牌谱动作或导入层另行补充。
  */
final case class PaifuTile(rank: Int, suit: PaifuTileSuit)
