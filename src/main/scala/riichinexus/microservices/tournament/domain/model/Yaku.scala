package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class Yaku(
    name: String,
    han: Int
) derives CanEqual:
  require(name.trim.nonEmpty, "Yaku name cannot be empty")
  require(han > 0, "Yaku han must be positive")

