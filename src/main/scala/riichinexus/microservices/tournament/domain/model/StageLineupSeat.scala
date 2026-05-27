package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class StageLineupSeat(
    playerId: PlayerId,
    preferredWind: Option[SeatWind] = None,
    reserve: Boolean = false
) derives CanEqual

