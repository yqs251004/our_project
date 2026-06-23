package riichinexus.microservices.tournament.domain.stage.model

import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

/** 穷举排桌搜索中的当前最优分数与分组方案。
  *
  * 排桌策略使用该模型在递归搜索中保留已发现的最低冲突分组结果。
  */
private[tournament] final case class SeatingSearchResult(
    score: Double,
    grouping: Vector[Vector[PlayerPrivateView]]
)
