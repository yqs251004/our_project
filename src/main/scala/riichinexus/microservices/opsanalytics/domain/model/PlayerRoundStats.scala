package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given

/** 从一局牌谱中提取的单个玩家行为统计。
  *
  * 该模型汇总向听轨迹、胜负结果、立直/副露、防守反应、受压弃和 exact 采样结果，是高级统计看板的中间输入。
  */
final case class PlayerRoundStats(
    shantenPath: Vector[Int],
    won: Boolean,
    dealtIn: Boolean,
    resultDelta: Int,
    riichiDeclared: Boolean,
    callCount: Int,
    pressureResponseCount: Int,
    postRiichiDealIn: Boolean,
    foldLikeResponse: Boolean,
    shantenImprovement: Double,
    exactUkeireSamples: Vector[Int],
    exactDefenseSampleCount: Int,
    exactSafeDefenseCount: Int,
    exactFoldCount: Int,
    strictTileTrackable: Boolean
)
