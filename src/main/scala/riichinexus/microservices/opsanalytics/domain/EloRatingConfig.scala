package riichinexus.microservices.opsanalytics.domain

final case class EloRatingConfig(
    kFactor: Int = 36,
    placementWeight: Double = 0.6,
    scoreWeight: Double = 0.3,
    umaWeight: Double = 0.1
) derives CanEqual:
  require(kFactor > 0, "kFactor must be positive")
  require(
    math.abs((placementWeight + scoreWeight + umaWeight) - 1.0) <= 0.0001,
    "Rating weights must sum to 1.0"
  )

object EloRatingConfig:
  val default: EloRatingConfig = EloRatingConfig()
