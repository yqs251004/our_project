package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given
/** EloRatingConfig 表示后端领域中的Elo评级配置 状态，包含kFactor、placementWeight、scoreWeight、umaWeight。 */
final case class EloRatingConfig(
    kFactor: Int = 36,
    placementWeight: Double = 0.6,
    scoreWeight: Double = 0.3,
    umaWeight: Double = 0.1
)