package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

final case class FinalStanding(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual
