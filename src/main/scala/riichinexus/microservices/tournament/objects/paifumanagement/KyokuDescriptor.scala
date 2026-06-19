package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

/** KyokuDescriptor 表示前后端共享的KyokuDescriptor 数据结构，包含roundWind、handNumber、honba。 */

final case class KyokuDescriptor(
    roundWind: SeatWind,
    handNumber: Int,
    honba: Int = 0
)
