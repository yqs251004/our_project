package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual

