package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

final case class PaifuRoundPlayer(
    playerId: PlayerId,
    seat: SeatWind,
    initialHand: PaifuHand,
    track: PaifuPlayerTrack
) derives CanEqual
