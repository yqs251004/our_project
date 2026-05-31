package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class SwissRuleConfig(
    pairingMethod: String = "balanced-elo",
    carryOverPoints: Boolean = true,
    maxRounds: Option[Int] = None
) derives CanEqual:
  private val supportedPairingMethods = Set("balanced-elo", "snake")
  require(
    supportedPairingMethods.contains(pairingMethod.trim.toLowerCase),
    s"Unsupported swiss pairing method: $pairingMethod"
  )

