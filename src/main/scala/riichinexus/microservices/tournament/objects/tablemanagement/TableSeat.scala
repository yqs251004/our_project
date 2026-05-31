package riichinexus.microservices.tournament.objects.tablemanagement

import riichinexus.domain.model.*

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
) derives CanEqual
