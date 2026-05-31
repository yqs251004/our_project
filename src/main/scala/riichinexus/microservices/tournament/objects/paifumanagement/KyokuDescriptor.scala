package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

final case class KyokuDescriptor(
    roundWind: SeatWind,
    handNumber: Int,
    honba: Int = 0
) derives CanEqual
