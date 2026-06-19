package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

/** FinalStanding 表示前后端共享的最终排名 数据结构，包含玩家 ID、座位、最终点数、名次、uma、oka。 */

final case class FinalStanding(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
)
