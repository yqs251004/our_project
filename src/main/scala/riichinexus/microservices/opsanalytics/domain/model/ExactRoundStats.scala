package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given

/** 牌谱可严格追踪时提取的 exact 级别小局统计。
  *
  * 它把有效进张采样和立直后防守行为压缩成一组计数，用来衡量高级统计结果中 exact 样本的覆盖率和可靠性。
  */
final case class ExactRoundStats(
    strictTileTrackable: Boolean,
    ukeireSamples: Vector[Int],
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int
)
