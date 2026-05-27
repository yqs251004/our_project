package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class KnockoutBracketResult(
    playerId: PlayerId,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives CanEqual

