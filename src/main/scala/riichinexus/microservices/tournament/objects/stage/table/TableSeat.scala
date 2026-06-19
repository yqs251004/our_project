package riichinexus.microservices.tournament.objects.stage.table

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** TableSeat 表示前后端共享的牌桌座位 数据结构，包含座位、玩家 ID、initialPoints、disconnected、ready、俱乐部 ID。 */

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
)
