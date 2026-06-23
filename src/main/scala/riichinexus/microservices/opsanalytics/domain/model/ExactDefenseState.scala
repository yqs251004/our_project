package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.microservices.player.objects.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 分析一局牌谱时跟踪立直后防守选择的临时状态。
  *
  * 它记录立直宣言、公开可见牌和弃牌安全性计数，用于判断玩家在压力下选择安全牌或弃和的频率。
  */
final case class ExactDefenseState(
    riichiDiscards: Map[PlayerId, Set[Int]],
    playerDeclaredRiichi: Boolean,
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int,
    publicVisible: Vector[Int]
)
