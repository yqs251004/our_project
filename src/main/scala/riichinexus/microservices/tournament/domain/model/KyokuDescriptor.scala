package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class KyokuDescriptor(
    roundWind: SeatWind,
    handNumber: Int,
    honba: Int = 0
) derives CanEqual:
  require(handNumber >= 1 && handNumber <= 4, "Hand number must be between 1 and 4")
  require(honba >= 0, "Honba must be non-negative")

