package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class KnockoutRuleConfig(
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Boolean = false,
    seedingPolicy: String = "rating",
    repechageEnabled: Boolean = false
) derives CanEqual:
  private val supportedPolicies = Set("rating", "elo", "ranking", "standings")
  require(
    supportedPolicies.contains(seedingPolicy.trim.toLowerCase),
    s"Unsupported knockout seeding policy: $seedingPolicy"
  )

