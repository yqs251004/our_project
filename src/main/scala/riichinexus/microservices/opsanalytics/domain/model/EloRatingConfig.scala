package riichinexus.microservices.opsanalytics.domain.model

final case class EloRatingConfig(
    kFactor: Int = 36,
    placementWeight: Double = 0.6,
    scoreWeight: Double = 0.3,
    umaWeight: Double = 0.1
)
