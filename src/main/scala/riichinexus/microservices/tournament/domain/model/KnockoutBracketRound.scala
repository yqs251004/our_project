package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives CanEqual

