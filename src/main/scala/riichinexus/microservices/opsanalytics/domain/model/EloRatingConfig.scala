package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given

/** Elo 变动计算使用的权重配置。
  *
  * `kFactor` 控制整体波动幅度，其余权重决定名次、原点差和 uma 在单场评分变化中的占比。
  */
final case class EloRatingConfig(
    kFactor: Int = 36,
    placementWeight: Double = 0.6,
    scoreWeight: Double = 0.3,
    umaWeight: Double = 0.1
)
