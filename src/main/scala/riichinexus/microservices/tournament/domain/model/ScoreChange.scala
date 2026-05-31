package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual

